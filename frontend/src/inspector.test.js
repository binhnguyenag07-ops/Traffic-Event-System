import { describe, expect, it } from 'vitest'
import { inspectorEntries } from './inspector'

describe('inspectorEntries', () => {
  it('keeps adaptive vehicle diagnostics visible', () => {
    const entries = inspectorEntries({
      kind: 'vehicle',
      data: {
        id: 'car-1',
        type: 'CAR',
        status: 'WAITING',
        waitCause: 'DOWNSTREAM_FULL',
        waitCauseSeconds: 18,
        blockingResource: 'JUNCTION_MOVEMENT:J:AJ>JB',
        navigationMode: 'EVALUATING',
        knowledgeVersion: 3,
        routeVersion: 5,
        nextPlannedMovement: 'JB',
        lastNavigationDecision: { accepted: false },
      },
    })

    expect(Object.fromEntries(entries.map((item) => [item.key, item.value])))
      .toMatchObject({
        waitCause: 'DOWNSTREAM_FULL',
        blockingResource: 'JUNCTION_MOVEMENT:J:AJ>JB',
        navigationMode: 'EVALUATING',
        knowledgeVersion: 3,
        routeVersion: 5,
        decisionResult: 'REJECTED',
      })
  })
})
