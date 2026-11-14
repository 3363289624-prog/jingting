"""Complete optional local Zira word-boundary fixtures; not used by the app."""
from pathlib import Path
import json, struct, wave

base = Path(__file__).resolve().parent
rows = json.loads((base / 'reference.json').read_text(encoding='utf-8-sig'))
for row in rows:
    with wave.open(str(base / 'speech' / row['file']), 'rb') as audio:
        if audio.getnchannels() != 1 or audio.getsampwidth() != 2 or audio.getframerate() != 16000:
            raise ValueError('Reference audio must use the documented 16 kHz mono PCM timebase')
        rate = audio.getframerate()
        samples = struct.unpack('<' + 'h' * audio.getnframes(), audio.readframes(audio.getnframes()))
        row['duration'] = len(samples) / rate
        row['speechEnd'] = (max(i for i, value in enumerate(samples) if abs(value) > 100) + 1) / rate
(base / 'word-reference.json').write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding='utf-8')
print('Wrote', base / 'word-reference.json')
