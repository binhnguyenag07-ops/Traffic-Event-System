// Builds the course submission package: an anonymised copy of the source tree
// plus the ZIP the assignment asks for.
//
// The file list comes from `git ls-files`, never from walking the working
// directory. That is the whole trick. A walk has to guess which of `target/`,
// `node_modules/`, `dist/`, `.tools/` and a dozen caches are build output, and
// it guesses wrong the first time somebody adds a new one. Git already knows,
// because .gitignore already said so, so the package is exactly the tracked
// source and nothing else.
//
// Anonymisation happens here rather than in the repository because the blind
// review needs a scrubbed copy while the team still needs to know who wrote
// what. Editing main would trade one for the other.
//
//   node tools/make_submission.mjs --group 01 --students 25121234,25121235
//
// Writes submission/<group>_<ids>/ (upload this to anonymous.4open.science)
// and submission/<group>_<ids>.zip (submit this).

import { execFileSync } from 'node:child_process'
import { cpSync, mkdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'

const ROOT = resolve(import.meta.dirname, '..')
const SIZE_LIMIT_BYTES = 20 * 1024 * 1024

/** Tracked files that are real work but not part of the delivered system. */
const OMIT = new Set([
  // Personal Vietnamese speaking notes for one member's viva. Not deliverable,
  // and it names the member in its title.
  'docs/STUDENT_C_PRESENTATION_NOTES.md',
  // This script. Its rename table spells out every real name it is removing, so
  // shipping it would undo the anonymisation in the most literal way possible.
  // The FORBIDDEN scan below catches this if the omission is ever removed.
  'tools/make_submission.mjs',
])

/**
 * Per-member planning notes, renamed to their role.
 *
 * The content of these files is technical and already impersonal; only the
 * filenames and the two index tables that link to them carry a name.
 */
const RENAME = new Map([
  ['docs/planning/Binh-network-persistence.md', 'docs/planning/student-a-network-persistence.md'],
  ['docs/planning/nguynie-routing-algorithms.md', 'docs/planning/student-b-routing-algorithms.md'],
  ['docs/planning/Hyo-vehicle-traffic_event.md', 'docs/planning/student-c-vehicle-traffic-event.md'],
  ['docs/planning/An-simulation-API-services-frontend.md', 'docs/planning/student-d-simulation-api-frontend.md'],
])

/**
 * Literal substitutions applied to every staged text file.
 *
 * Ordered longest-first within each group so a rename never eats the prefix of
 * a longer one it is a substring of.
 */
const SUBSTITUTIONS = [
  // The clone URL identifies the account that owns the repository.
  ['https://github.com/binhnguyenag07-ops/Traffic-Event-System.git', '<repository-url>'],
  // Link targets and index-table cells that follow the renames above.
  ['planning/Binh-network-persistence.md', 'planning/student-a-network-persistence.md'],
  ['planning/nguynie-routing-algorithms.md', 'planning/student-b-routing-algorithms.md'],
  ['planning/Hyo-vehicle-traffic_event.md', 'planning/student-c-vehicle-traffic-event.md'],
  ['planning/An-simulation-API-services-frontend.md', 'planning/student-d-simulation-api-frontend.md'],
  ['| Binh |', '| Student A |'],
  ['| Nguyie |', '| Student B |'],
  ['| Hyo |', '| Student C |'],
  ['| An |', '| Student D |'],
  // The README's documentation index points at the omitted notes file.
  ['- [Student C presentation notes](docs/STUDENT_C_PRESENTATION_NOTES.md)\n', ''],
]

/**
 * Anything matching these must not survive into the package.
 *
 * This runs after the substitutions as an independent check, so a name that
 * reappears in a file nobody thought to list still fails the build instead of
 * shipping. Case-insensitive; `Nguyen` alone is too common a Vietnamese
 * surname to flag, so only the handles and addresses are listed.
 */
const FORBIDDEN = [
  /binhnguyenag07/i,
  /anle6329/i,
  /phucnguyen\.7a3/i,
  /HyoL-2412/i,
  /hoccode1ngay/i,
  /Vo Minh Tri/i,
  /@student\.hcmus\.edu\.vn/i,
  /@clc\.fitus\.edu\.vn/i,
  /STUDENT_C_PRESENTATION_NOTES/,
]

const TEXT_EXTENSIONS = new Set([
  '.md', '.java', '.js', '.jsx', '.mjs', '.json', '.xml', '.html', '.css',
  '.py', '.sh', '.ps1', '.txt', '.yml', '.yaml', '.example',
])

function parseArguments(argv) {
  const parsed = {}
  for (let index = 0; index < argv.length; index += 1) {
    if (!argv[index].startsWith('--')) continue
    parsed[argv[index].slice(2)] = argv[index + 1]
  }
  const group = parsed.group
  const students = (parsed.students || '').split(',').map((id) => id.trim()).filter(Boolean)
  if (!group || students.length === 0) {
    console.error('usage: node tools/make_submission.mjs --group <id> --students <id1,id2,...>')
    process.exit(2)
  }
  // The assignment requires ascending student IDs. Sorting them here rather
  // than trusting the caller means the filename cannot be wrong.
  const sorted = [...students].sort()
  if (sorted.join() !== students.join()) {
    console.log('note: student IDs reordered into ascending order as required')
  }
  return { group, students: sorted }
}

function isText(path) {
  const dot = path.lastIndexOf('.')
  return dot !== -1 && TEXT_EXTENSIONS.has(path.slice(dot))
}

function main() {
  const { group, students } = parseArguments(process.argv.slice(2))
  const stem = [group, ...students].join('_')
  const stageRoot = join(ROOT, 'submission', stem)
  const zipPath = join(ROOT, 'submission', `${stem}.zip`)

  rmSync(join(ROOT, 'submission'), { recursive: true, force: true })
  mkdirSync(stageRoot, { recursive: true })

  const tracked = execFileSync('git', ['ls-files', '-z'], { cwd: ROOT })
    .toString('utf8')
    .split('\0')
    .filter(Boolean)

  let copied = 0
  let rewritten = 0
  for (const source of tracked) {
    if (OMIT.has(source)) continue
    const destination = join(stageRoot, RENAME.get(source) ?? source)
    mkdirSync(dirname(destination), { recursive: true })

    if (!isText(source)) {
      cpSync(join(ROOT, source), destination)
      copied += 1
      continue
    }

    const original = readFileSync(join(ROOT, source), 'utf8')
    let text = original
    for (const [from, to] of SUBSTITUTIONS) text = text.split(from).join(to)
    writeFileSync(destination, text)
    copied += 1
    if (text !== original) rewritten += 1
  }

  // Independent verification pass over what was actually written.
  const leaks = []
  for (const source of tracked) {
    if (OMIT.has(source) || !isText(source)) continue
    const staged = join(stageRoot, RENAME.get(source) ?? source)
    const text = readFileSync(staged, 'utf8')
    for (const pattern of FORBIDDEN) {
      if (pattern.test(text)) leaks.push(`${RENAME.get(source) ?? source}: ${pattern}`)
    }
  }
  for (const source of tracked) {
    const staged = RENAME.get(source) ?? source
    for (const pattern of FORBIDDEN) {
      if (pattern.test(staged)) leaks.push(`filename ${staged}: ${pattern}`)
    }
  }
  if (leaks.length > 0) {
    console.error('identifying content survived into the package:')
    for (const leak of leaks) console.error(`  ${leak}`)
    process.exit(1)
  }

  if (process.platform === 'win32') {
    execFileSync('powershell', [
      '-NoProfile', '-Command',
      `Compress-Archive -Path '${stageRoot}\\*' -DestinationPath '${zipPath}' -Force`,
    ], { stdio: 'inherit' })
  } else {
    execFileSync('zip', ['-rq', zipPath, stem], { cwd: join(ROOT, 'submission'), stdio: 'inherit' })
  }

  const bytes = statSync(zipPath).size
  const megabytes = (bytes / 1024 / 1024).toFixed(2)
  console.log(`staged  ${copied} files (${rewritten} anonymised, ${OMIT.size} omitted)`)
  console.log(`folder  submission/${stem}/   -> upload to anonymous.4open.science`)
  console.log(`archive submission/${stem}.zip  ${megabytes} MB`)
  if (bytes > SIZE_LIMIT_BYTES) {
    console.error(`FAIL: over the ${SIZE_LIMIT_BYTES / 1024 / 1024} MB limit`)
    process.exit(1)
  }
  console.log(`ok      under the 20 MB limit`)
}

main()
