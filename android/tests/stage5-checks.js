const STAGE='stage5';
async function stageTest(){
 await testFixture();check('旧设置默认跟随系统',cfg.refreshMode==='system');
 const native=window.JingtingMedia,calls=[];window.JingtingMedia={setRefreshMode:mode=>calls.push(['refresh',mode]),scheduleTimer:(id,ms)=>calls.push(['timer',id,ms]),cancelTimer:id=>calls.push(['cancel',id]),update:()=>{}};
 fillSet();$('#cfgRefresh').value='smooth';$('#cfgRefresh').onchange({target:$('#cfgRefresh')});check('高刷选项写入设置并调用原生桥',cfg.refreshMode==='smooth'&&calls.some(x=>x[0]==='refresh'&&x[1]==='smooth'));
 $('#cfgRefresh').value='system';$('#cfgRefresh').onchange({target:$('#cfgRefresh')});check('可恢复跟随系统',cfg.refreshMode==='system'&&JSON.parse(localStorage.getItem(CFG_KEY)).refreshMode==='system');
 let fired=0;const id=setPlaybackTimer(()=>fired++,1000);window.jingtingNativeTimer(id);window.jingtingNativeTimer(id);await testPause(1050);check('原生与网页双定时器只执行一次',fired===1&&!playbackTimers.has(id));
 const canceled=setPlaybackTimer(()=>fired++,30);clearPlaybackTimer(canceled);window.jingtingNativeTimer(canceled);await testPause(80);check('撤销任务后原生回调无法重播',fired===1);
 await startPractice({repeats:3,gap:1},0);await testUntil(()=>practice.phase==='gap','interruption in gap');window.jingtingAudioInterrupted('noisy');await testPause(1200);check('耳机断开撤销跟读间隙待播',audio.paused&&practice.phase==='paused'&&!practice.wait&&!practice.boundary);
 stopPractice();cur.bookmarks=cur.cues.slice(0,2).map(c=>({...c,key:bookmarkKey(c)}));await dbPut(cur);await startReviewQueue();await testUntil(()=>reviewQueue.wait,'review wait');window.jingtingAudioInterrupted('pause');await testPause(800);check('锁屏媒体暂停撤销复习待播',audio.paused&&reviewQueue.phase==='paused'&&!reviewQueue.wait);stopReviewQueue();stopPractice();
 await startPractice({repeats:2,gap:1},0);await testUntil(()=>practice.phase==='gap','sleep gap');sleepDeadline=Date.now()-1;checkSleepTimer();await testPause(1200);check('定时结束清理所有待播任务',audio.paused&&!practice.wait&&!reviewQueue.wait&&!sleepDeadline);stopPractice();
 check('暂停后关键定时器没有残留',playbackTimers.size===0);window.JingtingMedia=native;
}
