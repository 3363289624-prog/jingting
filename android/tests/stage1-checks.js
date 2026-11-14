const STAGE='stage1';
async function stageTest(){
 await testFixture();
 check('旧材料默认不偏移',subtitleOffset(cur)===0&&cues[0].s===.2);
 await toggleCurrentBookmark();const key=cur.bookmarks[0].key;
 cur.subtitleOffset=.5;refreshSubtitleTiming();
 check('整体偏移同步句子与VTT锚点',cues[0].s===.7&&cues[0].wordAnchors[0].s===.7&&cur.cues[0].s===.2);
 check('句子切换按整体偏移',findCue(1.2)===0&&findCue(1.6)===1);
 goToCue(1,false);await testPause(80);check('跳句使用偏移后的时间',Math.abs(audio.currentTime-1.505)<.03);
 check('收藏键与定位稳定',cur.bookmarks[0].key===key&&bookmarkResolver(cur)(cur.bookmarks[0])===0);
 cur.wordHighlightOffset=.1;check('词偏移与整体偏移分别保留',Math.abs(getWordTimeline(cues[0]).starts[0]-.7)<.001&&wordOffset()===.1&&displayCueIndex(1.51)===1);
 await startPractice({repeats:2,gap:0},0);await testUntil(()=>practice.phase==='complete','shifted practice');
 check('跟读起止与重复范围整体偏移',audio.paused&&audio.currentTime>=1.17&&audio.currentTime<1.35&&practice.pass===2);
 stopPractice();audio.playbackRate=2;await startPractice({repeats:2,gap:0},1);await testUntil(()=>practice.phase==='complete','double speed');check('两倍速跟读使用偏移范围',audio.currentTime>=1.96&&audio.currentTime<2.2);stopPractice();audio.playbackRate=1;
 await openSubtitleCalibration(cur);$('#subtitleLater').click();check('预览即时生效',subtitleOffset(cur)===.6);closeSheet();check('关闭取消预览',subtitleOffset(cur)===.5&&cur.subtitleOffset===.5);
 await openSubtitleCalibration(cur);$('#subtitleSeconds').value='-0.2';$('#subtitleSave').click();await testUntil(()=>!$('#sheet').classList.contains('on'),'save');check('保存持久化且原始字幕不变',(await dbGet(cur.id)).subtitleOffset===-.2&&cur.cues[0].s===.2);
 await openTrack(cur.id);await testUntil(()=>audio.readyState>=1,'reopen');check('重新打开应用材料使用已保存偏移',subtitleOffset(cur)===-.2&&cues[0].s===0);
 const restored=await parseBackup(await makeBackup([cur]));check('备份保留整体和逐词偏移',restored[0].subtitleOffset===-.2&&restored[0].wordHighlightOffset===.1);
 await openSubtitleCalibration(cur);$('#subtitleSeconds').value='99';$('#subtitleSeconds').onchange();check('偏移限制到30秒',subtitleOffset(cur)===30&&$('#subtitleLater').disabled);$('#subtitleSeconds').value='-99';$('#subtitleSeconds').onchange();check('负向范围限制',subtitleOffset(cur)===-30&&$('#subtitleEarlier').disabled);$('#subtitleReset').click();$('#subtitleSave').click();await testUntil(()=>!$('#sheet').classList.contains('on'),'reset');check('恢复默认可保存',subtitleOffset(cur)===0&&cues[0].s===.2);
 cur.subtitleOffset=-.4;refreshSubtitleTiming();goToCue(0,false);await testPause(80);check('负偏移跳句不产生负播放时间',audio.currentTime>=0&&audio.currentTime<.03);cur.subtitleOffset=30;refreshSubtitleTiming();check('超出音频的句子可识别',!cueInAudio(0));
}
