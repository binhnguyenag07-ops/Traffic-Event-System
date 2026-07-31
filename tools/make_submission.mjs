// Builds the course submission folder and ZIP from the tracked source tree.
//
// The file list comes from `git ls-files`, never from walking the working
// directory. That is the whole trick. A walk has to guess which of `target/`,
// `node_modules/`, `dist/`, `.tools/` and a dozen caches are build output, and
// it guesses wrong the first time somebody adds a new one. Git already knows,
// because .gitignore already said so, so the package is exactly the tracked
// source and nothing else.
//
//   node tools/make_submission.mjs --group 01 --students 25121234,25121235
//
// Writes submission/<group>_<ids>/ and submission/<group>_<ids>.zip.

import { execFileSync } from 'node:child_process'
import { cpSync, mkdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'

const ROOT = resolve(import.meta.dirname, '..')
const SIZE_LIMIT_BYTES = 20 * 1024 * 1024

/** Tracked files that are real work but not part of the delivered system. */
const OMIT = new Set([
  // Personal speaking notes are not part of the delivered system.
  'docs/STUDENT_C_PRESENTATION_NOTES.md',
  // The packaging utility is not part of the delivered application.
  'tools/make_submission.mjs',
])

/** Email addresses must not survive into the blind-review copy. */
const FORBIDDEN = [
  /[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i,
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
  for (const source of tracked) {
    if (OMIT.has(source)) continue
    const destination = join(stageRoot, source)
    mkdirSync(dirname(destination), { recursive: true })

    if (!isText(source)) {
      cpSync(join(ROOT, source), destination)
      copied += 1
      continue
    }

    writeFileSync(destination, readFileSync(join(ROOT, source), 'utf8'))
    copied += 1
  }

  // Verify the copied package rather than trusting the working tree.
  const leaks = []
  for (const source of tracked) {
    if (OMIT.has(source) || !isText(source)) continue
    const staged = join(stageRoot, source)
    const text = readFileSync(staged, 'utf8')
    for (const pattern of FORBIDDEN) {
      if (pattern.test(text)) leaks.push(`${source}: ${pattern}`)
    }
  }
  for (const source of tracked) {
    for (const pattern of FORBIDDEN) {
      if (pattern.test(source)) leaks.push(`filename ${source}: ${pattern}`)
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
  console.log(`staged  ${copied} files (${OMIT.size} omitted)`)
  console.log(`folder  submission/${stem}/`)
  console.log(`archive submission/${stem}.zip  ${megabytes} MB`)
  if (bytes > SIZE_LIMIT_BYTES) {
    console.error(`FAIL: over the ${SIZE_LIMIT_BYTES / 1024 / 1024} MB limit`)
    process.exit(1)
  }
  console.log(`ok      under the 20 MB limit`)
}

main()
