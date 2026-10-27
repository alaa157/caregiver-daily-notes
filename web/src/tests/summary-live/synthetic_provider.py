"""Loopback-only OpenAI-wire fixture for disposable integration tests; no real AI calls."""
import json
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Provider(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass  # Prompts, credentials and note contents must not enter logs.

    def do_POST(self):
        try:
            payload = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))))
            text = payload['messages'][-1]['content']
            if 'PROVIDER_UNAVAILABLE_FIXTURE' in text:
                self.send_response(503)
                self.end_headers()
                return
            if 'CURRENT MEDS:' in text and 'CATALOG:' in text:
                note_id = re.search(r'[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}', text).group()
                section = text.split('CURRENT MEDS:\n', 1)[1].split('CATALOG:', 1)[0]
                medications = []
                for line in section.splitlines():
                    if line.startswith('- '):
                        name, dose, schedule = line[2:].split('|')
                        medications.append(dict(name=name, dose=dose, schedule=schedule))
                content = dict(changes=[dict(actionId='SCHEDULE_CHECK_IN', params=dict(withinDays='7'),
                    reason='Review recorded observations.', evidenceNoteIds=[note_id])], proposedMedications=medications)
            else:
                notes = re.findall(r'\[NOTE id="([^"]+)"[^\n]*\]\n(.*?)\n\[/NOTE\]', text, re.S)
                note_id, note = notes[0]
                quote = note.split('Free text (verbatim):\n', 1)[1].splitlines()[0].strip()
                content = dict(observations=[dict(text='Recorded observation from the selected note.', noteId=note_id, quote=quote)],
                    uncertainties=[dict(topic='Fixture context', detail='Synthetic provider output for integration verification.')])
            body = json.dumps(dict(choices=[dict(message=dict(content=json.dumps(content, ensure_ascii=False)), finish_reason='stop')])).encode()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except (ValueError, KeyError, IndexError, AttributeError):
            self.send_response(400)
            self.end_headers()


if __name__ == '__main__':
    ThreadingHTTPServer(('127.0.0.1', 8099), Provider).serve_forever()
