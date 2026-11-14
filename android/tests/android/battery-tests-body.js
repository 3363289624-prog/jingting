const batteryResult={passed:false,status:'waiting-unplug',protocol:'ABBA: 60 s warmup, 10 s settle + 300 s per block, app brightness 35%, silent WAV, native loop, same 60 cues; USB unplug required',segments:[],error:''};
let originalConfig,originalSnapshot;
const sample=()=>JSON.parse(DeviceTests.sample());
const overlay=document.createElement('div');overlay.style.cssText='position:fixed;top:45px;left:8px;right:8px;z-index:99999;background:#1c1c1eee;color:white;padding:12px;font:14px/1.6 sans-serif;border-radius:12px';document.body.append(overlay);
const checkpoint=()=>DeviceTests.checkpoint(JSON.stringify(batteryResult));
async function batteryWait(seconds,label,record=false){
 const out=[],end=sample().elapsedMs+seconds*1000;
 while(sample().elapsedMs<end){
  const value=sample();if(!value.interactive||document.hidden)throw Error('屏幕关闭或应用离开前台，本轮测量无效');if(value.plugged!==0)throw Error('检测到 USB/充电接入，本轮耗电测量无效');if(audio.paused)throw Error('播放被打断，本轮测量已停止');
  if(record)out.push(value);overlay.textContent=label+' · 剩余 '+Math.ceil((end-value.elapsedMs)/1000)+' 秒。请保持精听在前台，完成后再接回 USB。';
  await testPause(5000);
 }
 return out;
}
try{
 while(!db&&!memMode)await testPause(20);
 originalConfig={...cfg};originalSnapshot=JSON.stringify((await dbAll()).map(t=>({...t,blob:{size:t.blob?.size,type:t.blob?.type}})));
 memMode=true;mem.clear();await testFixture();
 const fixture={...cur,blob:testWav(60),duration:60,cues:Array.from({length:60},(_,i)=>({s:i,e:i+.95,en:'Listen carefully and practise a little every day.',zh:'每天认真听，练习一点。'}))};await dbPut(fixture);await openTrack(fixture.id);await testUntil(()=>audio.readyState>=1,'battery audio');
 audio.loop=true;audio.muted=false;audio.volume=.02;
 const capability=sample();if(!(capability.chargeUah>0))throw Error('手机未提供可用的电量计数器，无法做有效耗电对照');
 checkpoint();DeviceTests.stage('battery-ready-unplug');overlay.textContent='耗电对照已准备：请解锁并保持精听在前台，然后拔下 USB。约 22 分钟后显示完成，再接回电脑。';
 const deadline=Date.now()+600000;while(sample().plugged!==0){if(Date.now()>deadline)throw Error('等待拔线超时，未开始耗电测量');await testPause(1000);}
 cfg.refreshMode='system';applyRefreshPreference();await audio.play();batteryResult.status='running';checkpoint();await batteryWait(60,'预热');
 for(const [i,mode]of ['system','smooth','smooth','system'].entries()){
  cfg.refreshMode=mode;applyRefreshPreference();await batteryWait(10,'切换模式稳定');
  const start=sample(),samples=await batteryWait(300,`第 ${i+1}/4 段：${mode==='system'?'跟随系统':'优先流畅'}`,true),end=sample();
  const usedUah=start.chargeUah-end.chargeUah,durationSec=(end.elapsedMs-start.elapsedMs)/1000;
  batteryResult.segments.push({mode,start,end,durationSec,usedUah,averageDischargeMa:usedUah*3.6/durationSec,refreshHz:[...new Set(samples.map(x=>x.refreshHz))],samples});checkpoint();
 }
 batteryResult.passed=batteryResult.segments.every(s=>s.usedUah>0&&s.start.plugged===0&&s.end.plugged===0);batteryResult.status=batteryResult.passed?'complete':'invalid-counter';if(!batteryResult.passed)batteryResult.error='电量计数器未稳定下降，不能据此比较耗电。';
}catch(e){batteryResult.status='failed';batteryResult.error=e.message||String(e);}
finally{
 audio.loop=false;releaseCurrentTrack();mem.clear();memMode=false;if(originalConfig){cfg=originalConfig;saveCfg();applyRefreshPreference();}
 batteryResult.originalDataUnchanged=originalSnapshot===JSON.stringify((await dbAll()).map(t=>({...t,blob:{size:t.blob?.size,type:t.blob?.type}})));
 await renderLib();show('vLib');checkpoint();overlay.textContent=batteryResult.passed?'耗电对照完成，请接回 USB。原材料保持不变。':'耗电对照已停止：'+batteryResult.error+'。请接回 USB。';DeviceTests.done(JSON.stringify(batteryResult));
}
