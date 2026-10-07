import {ApiClient} from '../lib/ApiClient';
import {FakeTransport} from '../lib/FakeTransport';
import {
  ARABIC_NOTE,
  EG_NOTE,
  ENGLISH_NOTE,
  PLAN,
  RECIPIENT,
  SUMMARY_WITH_FLAGS,
} from '../test-utils/fixtures';

// Part A backend-less client: deterministic scripted responses from synthetic
// fixtures only. No fetch, no network, no backend assumed. Part B rebinds
// AuthProvider to `new ApiClient(new FetchTransport())` (Task B1); the
// FetchTransport class stays in lib for that phase.
export function createPartAClient(): ApiClient {
  return new ApiClient(
    new FakeTransport({
      'POST /api/auth/login': {status: 200, body: {token: 'part-a-token'}},
      'POST /api/auth/register': {status: 200, body: {token: 'part-a-token'}},
      'GET /api/recipients': {status: 200, body: [RECIPIENT]},
      // Write routes return static fixture-shaped bodies; containers merge the
      // submitted values locally so the UI stays truthful.
      'POST /api/recipients': {status: 200, body: {...RECIPIENT, id: 'r-new'}},
      'POST /api/notes': {status: 200, body: {...ENGLISH_NOTE, id: 'n-part-a-1'}},
      // Addendum route is keyed on the deterministic Part A note id above.
      'POST /api/notes/n-part-a-1/addenda': {
        status: 200,
        body: {id: 'a-part-a-1', noteId: 'n-part-a-1', createdAt: '2026-10-04', text: ''},
      },
      'GET /api/notes?recipientId=r1': {status: 200, body: [ENGLISH_NOTE, EG_NOTE]},
      'GET /api/notes?recipientId=r2': {status: 200, body: [ARABIC_NOTE]},
      'POST /api/summaries': {status: 200, body: SUMMARY_WITH_FLAGS},
      'GET /api/plans': {status: 200, body: [PLAN]},
      'POST /api/plans/p1/accept': {status: 200, body: PLAN},
      'POST /api/plans/p1/edit-accept': {status: 200, body: PLAN},
      'POST /api/plans/p1/dismiss': {status: 200, body: PLAN},
      'POST /api/plans/p1/archive': {status: 200, body: PLAN},
    }),
  );
}
