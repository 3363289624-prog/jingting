const runLifecycleRegression = async () => {
  const report=document.createElement('pre');report.id='lifecycleReport';report.dataset.status='running';report.style.cssText='position:fixed;inset:8px;z-index:9999;background:#1c1c1e;color:#fff;overflow:auto;padding:16px;white-space:pre-wrap';document.body.appendChild(report);
  const results=[],pause=ms=>new Promise(r=>setTimeout(r,ms));
  const until=async(test)=>{const end=Date.now()+8000;while(!test()){if(Date.now()>end)throw new Error('timeout');await pause(15);}};
  const check=(name,value)=>{results.push({name,passed:!!value});report.textContent=JSON.stringify(results,null,2);if(!value)throw new Error(name);};
  let demo,wordDemo;
  try {
    while(!db&&!memMode)await pause(20);
    const before=await dbAll();
    if(before.some(t=>!['study-preview','word-preview','lifecycle-regression'].includes(t.id)))throw new Error('This suite requires an isolated test origin with only its generated preview record.');
    wordDemo=await dbGet('word-preview');demo=await dbGet('study-preview');if(!demo)throw new Error('Run the study suite first to create the disposable preview.');
    audio.muted=true;
    const fixture={...demo,id:'lifecycle-regression',name:'删除与清空临时材料'};
    await dbPut(fixture);await openTrack(fixture.id);await until(()=>cur?.id===fixture.id&&curIdx>=0&&audio.readyState>=1);
    openMaterialMenu(cur);$('#deleteTrack').click();$('#confirmAccept').click();await until(()=>cur===null);
    check('deleting the last opened track releases audio and current metadata',audio.getAttribute('src')===null&&!practice.active);
    savePlaybackProgress(true);await pause(100);
    check('later progress saves cannot recreate a deleted track',!(await dbGet(fixture.id)));
    await dbPut(fixture);await openTrack(fixture.id);await until(()=>cur?.id===fixture.id&&curIdx>=0&&audio.readyState>=1);
    await startPractice({repeats:2,gap:1},0);await until(()=>practice.phase==='gap');
    openSettings();$('#btnClear').click();$('#confirmAccept').click();await until(()=>cur===null&&!practice.active);
    savePlaybackProgress(true);await pause(1200);
    check('clear-all cancels a pending practice gap and leaves storage empty',audio.paused&&!practice.wait&&!practice.boundary&&(await dbAll()).length===0&&audio.getAttribute('src')===null);
    $('#setBack').click();
    check('return after clear-all shows the empty library',!!$('#vLib.on')&&!!$('#libList .empty'));
    report.dataset.status='passed';
  } catch(error){results.push({name:'failure: '+error.message,passed:false});report.dataset.status='failed';}
  finally {
    releaseCurrentTrack();
    if(demo)await dbPut(demo);if(wordDemo)await dbPut(wordDemo);await dbDel('lifecycle-regression');await renderLib();
    report.textContent=JSON.stringify(results,null,2);
  }
};
const runLifecycleButton=document.createElement('button');runLifecycleButton.id='runLifecycleTests';runLifecycleButton.textContent='开始隔离数据回归';runLifecycleButton.style.cssText='position:fixed;inset:40%;z-index:99999;background:#007aff;color:#fff;border-radius:12px;padding:16px';document.body.appendChild(runLifecycleButton);runLifecycleButton.onclick=()=>{runLifecycleButton.remove();runLifecycleRegression();};
