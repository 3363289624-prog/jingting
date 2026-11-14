from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
from pathlib import Path
import argparse,json
parser=argparse.ArgumentParser();parser.add_argument('--root',type=Path,required=True);parser.add_argument('--port',type=int,default=8778);args=parser.parse_args();root=args.root.resolve()
class Handler(SimpleHTTPRequestHandler):
    def __init__(self,*a,**kw):super().__init__(*a,directory=str(root),**kw)
    def do_POST(self):
        if self.path!='/report':self.send_error(404);return
        result=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        (root/'browser-report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
        self.send_response(200);self.end_headers();self.wfile.write(b'OK')
print('Only use a disposable test origin:',f'http://127.0.0.1:{args.port}/integration.html')
ThreadingHTTPServer(('127.0.0.1',args.port),Handler).serve_forever()
