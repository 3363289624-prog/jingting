const STAGE='stage4';
async function stageTest(){
 await testFixture();const old=cur;old.progress=2;old.subtitleOffset=.2;old.blindMode=true;old.bookmarks=[{...old.cues[0],key:bookmarkKey(old.cues[0]),mastered:true}];await dbPut(old);
 const a=new File([testWav()],'New lesson.wav',{type:'audio/wav'}),b=new File([testWav(4)],old.name+'.wav',{type:'audio/wav'}),sub=new File(['1\n00:00:00,200 --> 00:00:00,700\nImported sentence\n'],'Wrong subtitle.srt',{type:'text/plain'});
 let plan=makeImportPlan([a,b],[sub],await dbAll());check('同名音频默认跳过',plan.audioRows[1].action==='skip'&&plan.audioRows[1].old.id===old.id);
 const before=(await dbAll()).length,preview=reviewImportPlan(plan);check('导入预览展示音频字幕和覆盖提示',!!$('#importPreview')&&$('#importPreview').textContent.includes('替换已有音频')&&!!$('[data-import-sub="0"]'));closeSheet();check('关闭取消且没有写入',!await preview&&(await dbAll()).length===before);
 plan.subRows[0].target='a0';let promise=reviewImportPlan(plan);$('[data-import-sub="0"]').value='a1';$('[data-import-sub="0"]').onchange();check('手动改配字幕到已有材料',plan.subRows[0].target==='a1'&&!$('#confirmImport').disabled);closeSheet();await promise;
 plan.audioRows[0].action='skip';plan.subRows[0].target='a0';check('阻止字幕指向跳过的新音频',!!importPlanError(plan));
 plan.audioRows[0].action='import';plan.audioRows[1].action='import';plan.subRows[0].target='a0';const records=await commitImportPlan(plan);check('确认后新增与同名替换一起写入',records.length===2&&(await dbAll()).length===before+1);
 const replaced=await dbGet(old.id),fresh=records.find(t=>t.id!==old.id);check('替换音频更新时长并保留偏好收藏',replaced.duration===4&&replaced.subtitleOffset===.2&&replaced.blindMode&&replaced.bookmarks[0].mastered&&replaced.progress===2);
 check('手动字幕配对写入正确材料',fresh.cues[0].en==='Imported sentence'&&replaced.cues[0].en==='One small step.');
 const broken=new File(['invalid subtitle'],'broken.srt',{type:'text/plain'}),c=new File([testWav()],'Atomic lesson.wav',{type:'audio/wav'});plan=makeImportPlan([c],[broken],await dbAll());plan.subRows[0].target='a0';const count=(await dbAll()).length;let failed=false;try{await commitImportPlan(plan);}catch(_){failed=true;}check('字幕无效时本批无部分写入',failed&&(await dbAll()).length===count);
 plan=makeImportPlan([a,a],[],await dbAll());plan.audioRows.forEach(r=>r.action='import');check('阻止同批重复音频写入同一材料',!!importPlanError(plan));
 plan=makeImportPlan([], [sub,sub],await dbAll());plan.subRows.forEach(r=>r.target='t:'+old.id);check('阻止多份字幕同时覆盖同一材料',!!importPlanError(plan));
 const pending=importBatch([new File([testWav()],'Cancelled lesson.wav',{type:'audio/wav'})],false);await testUntil(()=>!!$('#importPreview')&&$('#sheet').classList.contains('on')&&importing,'batch preview');$('#cancelImport').click();await pending;check('完整导入流程取消后解除忙碌状态',!importing&&!(await dbAll()).some(t=>t.name==='Cancelled lesson'));
 const newAudio=new File([testWav()],'Matched lesson.wav',{type:'audio/wav'}),newSub=new File(['1\n00:00:00,000 --> 00:00:00,500\nMatched words\n'],'Matched lesson.srt',{type:'text/plain'});
 const pending2=importBatch([newAudio,newSub],false);await testUntil(()=>!!$('#importPreview')&&$('#sheet').classList.contains('on')&&importing,'matched preview');check('同名自动配对可供确认',$('[data-import-sub="0"]').value==='a0');$('#confirmImport').click();await pending2;check('确认导入完整流程成功',(await dbAll()).some(t=>t.name==='Matched lesson'&&t.cues?.[0].en==='Matched words')&&!importing);
}
