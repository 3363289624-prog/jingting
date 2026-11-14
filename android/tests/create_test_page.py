"""Create a disposable, self-contained browser regression page from the app."""
from pathlib import Path
import argparse

parser = argparse.ArgumentParser()
parser.add_argument('--source', type=Path, default=Path(__file__).resolve().parents[1] / 'assets/index.html')
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--suite', choices=['ui','study','lifecycle','word'], default='ui')
args = parser.parse_args()
if args.source.resolve() == args.output.resolve():
    parser.error('The test output must not overwrite assets/index.html')
script = Path(__file__).with_name({'ui':'ui-regression.js','study':'study-regression.js','lifecycle':'lifecycle-regression.js','word':'word-regression.js'}[args.suite]).read_text(encoding='utf-8')
html = args.source.read_text(encoding='utf-8')
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(html.replace('</body>', '<script>' + script + '</script></body>'), encoding='utf-8')
print(args.output.resolve())
print('Run only on a disposable localhost origin; this page creates and removes test records.')
