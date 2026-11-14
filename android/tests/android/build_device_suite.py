from pathlib import Path
import shutil,sys,argparse,wave
root=Path(__file__).resolve().parent.parent
app=root.parent
parser=argparse.ArgumentParser();parser.add_argument('--output-dir',type=Path,required=True);parser.add_argument('--native-only',action='store_true');args=parser.parse_args()
helper=args.output_dir.resolve();helper.mkdir(parents=True,exist_ok=True)
shutil.copytree(root/'android/src',helper/'src',dirs_exist_ok=True);shutil.copy2(root/'android/AndroidManifest.xml',helper/'AndroidManifest.xml')
(helper/'assets').mkdir(parents=True,exist_ok=True)
shutil.copytree(app/'res',helper/'res',dirs_exist_ok=True)
harness=(root/'feature-harness.js').read_text(encoding='utf-8').split('async function runStageTest()',1)[0]
harness=harness.replace('setTimeout(r,ms)','setPlaybackTimer(r,ms)')
harness=harness.replace('throw Error(name);',"throw Error(name+' '+JSON.stringify({time:audio.currentTime,paused:audio.paused,phase:practice.phase,pass:practice.pass,index:curIdx,start:cues[curIdx]?.s,end:cues[curIdx]?.e}));")
parts=[]
for stage in ([] if args.native_only else range(1,5)):
 script=(root/f'stage{stage}-checks.js').read_text(encoding='utf-8')
 parts.append("DeviceTests.stage('stage%d');await(async()=>{%s\n%s\ntry{await stageTest();}finally{all.push({stage:STAGE,checks:testResults});}})();"%(stage,harness,script))
native=r'''
DeviceTests.stage('native');
await(async()=>{
HARNESS
await testFixture();audio.muted=false;audio.volume=.05;
all.push({stage:'native',checks:testResults});
const actualBridge=window.JingtingMedia;
DeviceTests.stage('native-refresh');
cfg.refreshMode='system';applyRefreshPreference();await testPause(120);check('真机默认不请求高刷',DeviceTests.refreshRate()===0);
cfg.refreshMode='smooth';applyRefreshPreference();await testPause(120);check('真机可请求120Hz',DeviceTests.refreshRate()>=119&&DeviceTests.refreshRate()<=121);
cfg.refreshMode='system';saveCfg();applyRefreshPreference();await testPause(120);check('真机恢复跟随系统',DeviceTests.refreshRate()===0);
DeviceTests.stage('native-timers');
let fired=0;const id=setPlaybackTimer(()=>fired++,60);await testUntil(()=>fired===1,'native timer');await testPause(100);check('真机原生回调与网页回调去重',fired===1);
DeviceTests.stage('native-rapid-toggle');
for(let n=0;n<20;n++){await audio.play();audio.pause();await testPause(25);}
await testPause(1200);check('真机快速启停不造成服务超时',audio.paused);
DeviceTests.stage('native-noisy');
await startPractice({repeats:3,gap:1},0);await testUntil(()=>practice.phase==='gap','native noisy gap');DeviceTests.noisy();await testPause(1300);check('真机接收器路径取消耳机断开后的待播',audio.paused&&!practice.wait&&!practice.boundary&&practice.phase==='paused');stopPractice();
cur.cues=[{s:0,e:7.7,en:'Lock screen playback test',zh:''}];cues=playbackCues(cur);renderCues();setActive(0,false);audio.currentTime=0;await audio.play();
DeviceTests.stage('ready-lock-screen');const start=audio.currentTime;await testPause(5000);check('锁屏后真实媒体时钟继续前进',audio.currentTime-start>4&&!audio.paused);audio.pause();
await startPractice({repeats:3,gap:1},0);DeviceTests.stage('ready-focus-loss');await testUntil(()=>audio.paused&&practice.phase==='paused','focus interruption',5000);check('其他应用获取音频焦点后暂停',audio.paused&&practice.phase==='paused'&&!practice.boundary&&!practice.wait);stopPractice();
})();
'''.replace('HARNESS',harness)
script=r'''(async()=>{
const all=[];let error='',original,originalConfig;
try{
while(!db&&!memMode)await new Promise(r=>setTimeout(r,20));
original=JSON.stringify((await dbAll()).map(t=>({...t,blob:{size:t.blob?.size,type:t.blob?.type}})));
originalConfig={...cfg};
memMode=true;mem.clear();
const nativeHandle=audio.handle?.bind(audio);if(nativeHandle){window.deviceTransportTrace=[];audio.handle=(token,type,state)=>{deviceTransportTrace.push({type,state,op:audio._operation,paused:audio.paused,seeking:audio.seeking});if(deviceTransportTrace.length>60)deviceTransportTrace.shift();return nativeHandle(token,type,state);};}
STAGES
NATIVE
}catch(e){error=(e.stack||String(e))+JSON.stringify(window.deviceTransportTrace?.slice(-18)||[]);}
finally{
stopReviewQueue();stopPractice();audio.pause();releaseCurrentTrack();mem.clear();memMode=false;
if(originalConfig){cfg=originalConfig;cfg.refreshMode='system';saveCfg();applyRefreshPreference();}
const current=JSON.stringify((await dbAll()).map(t=>({...t,blob:{size:t.blob?.size,type:t.blob?.type}})));
all.push({stage:'data',checks:[{name:'原有材料与元数据未改动',passed:current===original}]});
await renderLib();show('vLib');
DeviceTests.done(JSON.stringify({passed:!error&&all.every(g=>g.checks.every(t=>t.passed)),groups:all,error}));
}
})();'''.replace('STAGES','\n'.join(parts)).replace('NATIVE',native)
(helper/'assets/device-tests.js').write_text(script,encoding='utf-8')

(helper/'assets/index.html').write_text('<!doctype html><title>Local tests</title>',encoding='utf-8')
with wave.open(str(helper/'assets/focus.wav'),'wb')as wav:
 wav.setnchannels(1);wav.setsampwidth(2);wav.setframerate(8000);wav.writeframes(bytes(16000))
body=(root/'android/battery-tests-body.js').read_text(encoding='utf-8')
(helper/'assets/battery-tests.js').write_text('(async()=>{'+harness+body+'})();',encoding='utf-8')
print('Generated temporary device suite:',helper)
