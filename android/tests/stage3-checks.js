const STAGE='stage3';
async function stageTest(){
 await testFixture();
 openJumpSheet();check('盲听入口位于目录二级菜单',!!$('#blindFromDirectory'));$('#blindFromDirectory').click();check('二级菜单可开启盲听',!!$('#blindToggle'));$('#blindToggle').click();await testUntil(()=>isBlind()&&!!$('#blindReveal')&&!$('#sheet').classList.contains('on'),'blind saved');
 check('默认隐藏英文和译文且保留四个工具',!$('#cues').textContent.includes('One small')&&!$('#cues').textContent.includes('一小步')&&!!$('#blindReveal')&&$$('#vPlay .tools .tool').length===4);
 check('模式选择不泄露字幕',$('#seg').hidden&&$$('#cues .word').length===0);
 $('#blindReveal').click();check('点击揭晓仅显示当前句',$('#cues').textContent.includes('One small step')&&!$('#cues').textContent.includes('Listen and repeat')&&blindRevealedIndex===0);
 $('#blindReplay').click();await testUntil(()=>!audio.paused,'replay');check('揭晓后可以从当前句首重听',audio.currentTime<.4&&blindRevealedIndex===0);audio.pause();
 goToCue(1,false);check('切换句子自动隐藏',!!$('#blindReveal')&&!$('#cues').textContent.includes('Listen and repeat')&&blindRevealedIndex===-1);
 $('#blindReveal').click();cur.tr[1]='translated';applyTr();check('译文更新保持只揭晓当前句',!!$('#cues .cue')&&!$('#cues').textContent.includes('One small step'));
 await openTrack(cur.id);await testUntil(()=>audio.readyState>=1&&curIdx>=0,'blind reopen');check('重新打开材料仍盲听且重新隐藏',isBlind()&&blindRevealedIndex===-1&&!$('#cues .word'));
 const restored=await parseBackup(await makeBackup([cur]));check('材料盲听偏好进入备份',restored[0].blindMode===true);
 cur.bookmarks=[{...cur.cues[0],key:bookmarkKey(cur.cues[0]),savedAt:1},{...cur.cues[1],key:bookmarkKey(cur.cues[1]),savedAt:2}];await dbPut(cur);
 await startReviewQueue();await testUntil(()=>!audio.paused,'blind queue');check('收藏队列支持盲听',isBlind()&&!!$('#blindReveal'));$('#blindReveal').click();$('#blindReplay').click();await testUntil(()=>!audio.paused,'blind queue replay');check('盲听重听保留收藏队列',reviewQueue.active&&reviewQueue.at===0);await testUntil(()=>reviewQueue.at===1&&!audio.paused,'blind queue next');check('收藏下一句重新隐藏',blindRevealedIndex===-1&&!!$('#blindReveal'));stopReviewQueue();stopPractice();audio.pause();
 await setBlindMode(false);check('关闭盲听恢复普通字幕与切换模式',!isBlind()&&!$('#seg').hidden&&$$('#cues .cue').length===3);
 const realPut=dbPut;dbPut=()=>Promise.reject(Error('test'));await setBlindMode(true);dbPut=realPut;check('设置保存失败回滚',!isBlind());
}
