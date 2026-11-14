"""Generate disposable browser test pages. Serve them on a dedicated localhost origin."""
from pathlib import Path
import argparse

parser=argparse.ArgumentParser()
parser.add_argument('--output-dir',type=Path,required=True)
args=parser.parse_args()
tests=Path(__file__).resolve().parent
source=tests.parent/'assets/index.html'
output=args.output_dir.resolve();output.mkdir(parents=True,exist_ok=True)
if output==source.parent.resolve():parser.error('Never place tests in production assets')
harness=(tests/'feature-harness.js').read_text(encoding='utf-8').split('async function runStageTest()',1)[0]
parts=[]
for stage in range(1,6):
    script=(tests/f'stage{stage}-checks.js').read_text(encoding='utf-8')
    parts.append("status.textContent='第 %d 项回归中';await(async()=>{%s\n%s\nawait stageTest();groups.push({stage:STAGE,checks:testResults});})();"%(stage,harness,script))
for suite,fn,button,report in [('study','runStudyRegression','const studyRunButton','studyReport'),('word','runWordRegression','const wordRun=','wordReport'),('ui',None,None,'testReport'),('lifecycle','runLifecycleRegression','const runLifecycleButton','lifecycleReport')]:
    script=(tests/f'{suite}-regression.js').read_text(encoding='utf-8')
    script=script.split(button,1)[0]+f'\nawait {fn}();' if fn else 'await '+script
    if suite=='study':parts.append('releaseCurrentTrack();await dbClear();show("vLib");')
    if suite=='ui':parts.append('releaseCurrentTrack();show("vLib");await renderLib();')
    parts.append("status.textContent='%s 回归中';await(async()=>{%s\n})();{const report=$('#%s'),checks=JSON.parse(report.textContent);groups.push({stage:'%s',checks});report.remove();if(checks.some(t=>!t.passed))throw Error('%s regression failed');}"%(suite,script,report,suite,suite))
script="""const button=document.createElement('button');button.textContent='开始完整集成回归';button.style.cssText='position:fixed;inset:40%;z-index:99999;background:#007aff;color:white;padding:16px';document.body.append(button);button.onclick=async()=>{button.remove();const status=document.createElement('pre');status.style.cssText='position:fixed;top:0;left:0;z-index:99999;background:#181818;color:white;padding:16px;max-height:90vh;overflow:auto';document.body.append(status);const groups=[];let error='';try{PARTS}catch(e){error=e.stack||String(e);}finally{releaseCurrentTrack();const result={stage:'integration',passed:!error,groups,error};status.textContent=JSON.stringify(result,null,2);await fetch('/report',{method:'POST',body:JSON.stringify(result)});}};""".replace('PARTS','\n'.join(parts))
html=source.read_text(encoding='utf-8')
(output/'integration.html').write_text(html.replace('</body>','<script>'+script+'</script></body>'),encoding='utf-8')
print('Generated',output/'integration.html')
print('WARNING: This page clears only its own test-origin database. Use a fresh localhost port, never the production origin.')
