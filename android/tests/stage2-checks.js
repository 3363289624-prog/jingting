const STAGE='stage2';
async function stageTest(){
 await testFixture();const track=cur;track.name="B practice";
 track.practicePrefs={repeats:5,gap:3};track.subtitleOffset=.2;refreshSubtitleTiming();
 track.bookmarks=track.cues.map((c,i)=>({...c,key:bookmarkKey(c),savedAt:i+1}));await dbPut(track);
 const other={...track,id:'stage-other',name:'A practice',subtitleOffset:0,bookmarks:[{...track.cues[0],key:bookmarkKey(track.cues[0]),savedAt:5}]};await dbPut(other);
 let queue=buildReviewQueue([track,other]);check('顺序队列按材料与句子顺序',queue.length===4&&queue[0].trackId===other.id&&queue[1].key===track.bookmarks[0].key);
 const shuffled=buildReviewQueue([track,other],'random',()=>0);check('随机队列不遗漏不重复',shuffled.length===4&&new Set(shuffled.map(x=>x.trackId+x.key)).size===4&&JSON.stringify(shuffled)!==JSON.stringify(queue));
 await setBookmarkMastered(track.id,track.bookmarks[1].key,true);check('掌握状态持久化且排除队列',(await dbGet(track.id)).bookmarks[1].mastered===true&&buildReviewQueue([cur,other]).length===3);
 await setBookmarkMastered(track.id,track.bookmarks[1].key,false);check('取消掌握可以再次复习',buildReviewQueue([cur,other]).length===4);
 const savedKey=cur.bookmarks[0].key,realPut=dbPut;dbPut=()=>Promise.reject(Error('test'));check('掌握写入失败返回失败',!await setBookmarkMastered(cur.id,savedKey,true));dbPut=realPut;check('写入失败回滚掌握状态',!cur.bookmarks[0].mastered);
 await openBookmarksSheet();check('收藏列表提供顺序随机和掌握入口',!!$('#reviewSequential')&&!!$('#reviewRandom')&&!!$('[data-master="0"]'));closeSheet();
 await startReviewQueue('sequential');await testUntil(()=>reviewQueue.phase==='playing'&&!audio.paused,'review starts');check('队列跨材料开启单句播放',reviewQueue.active&&cur.id===other.id&&practice.repeats===1);
 await testUntil(()=>reviewQueue.wait,'review gap');window.jingtingMediaCommand('pause');await testPause(800);check('队列间隙暂停不会继续下一句',reviewQueue.active&&reviewQueue.phase==='paused'&&audio.paused&&!reviewQueue.wait&&reviewQueue.at===0);
 window.jingtingMediaCommand('play');await testUntil(()=>cur.id===track.id&&!audio.paused,'resume next track');check('恢复继续待播下一句并应用整体校准',reviewQueue.at===1&&curIdx===0&&cues[0].s===.4&&audio.currentTime>=.4);
 check('队列不覆盖用户跟读偏好',cur.practicePrefs.repeats===5&&cur.practicePrefs.gap===3);
 window.jingtingMediaCommand('pause');await masterReviewItem();await testUntil(()=>reviewQueue.at===2&&!audio.paused,'master advances');check('当前句已掌握后自动下一句',cur.bookmarks[0].mastered===true&&curIdx===1);
 jumpCue(1);await testUntil(()=>reviewQueue.at===3&&!audio.paused,'next queue item');check('下一句按钮沿收藏队列移动',curIdx===2);
 await testUntil(()=>!reviewQueue.active,'queue completion');check('队列完成后暂停且清理任务',audio.paused&&!practice.active&&!reviewQueue.wait);
 const restored=await parseBackup(await makeBackup([cur]));check('备份保留已掌握状态',restored[0].bookmarks[0].mastered===true);
 await startReviewQueue();await testUntil(()=>!audio.paused,'second queue');await openTrack(track.id);await testPause(800);check('手动换材料结束队列和待播任务',!reviewQueue.active&&audio.paused&&!reviewQueue.wait);
 await startReviewQueue();await testUntil(()=>!audio.paused,'queue for back');$('#btnBack').click();await testPause(800);check('返回材料库结束复习',!reviewQueue.active&&audio.paused&&!practice.active);
 await dbDel(other.id);
}
