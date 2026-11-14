const fs=require('fs'),vm=require('vm'),assert=require('assert');
const path=require('path');
const sourcePath=process.argv[2]||path.resolve(__dirname,'../assets/index.html');
const outputPath=process.argv[3]||path.join(__dirname,'word-core-results.json');
const html=fs.readFileSync(sourcePath,'utf8'),start=html.indexOf('/* 词形、发音时长'),end=html.indexOf('/* 仅在字幕内容完全对应',start);
assert(start>=0&&end>start);
const api=vm.runInNewContext('const CJK=/[\\u4e00-\\u9fff\\u3400-\\u4dbf]/;\n'+html.slice(start,end)+'\n;({speechTokens,englishSyllables,speechWeight,buildWordTimeline,timelineWordIndex,parseSRT,subtitleTimestamp})');
const results=[];
function test(name,fn){try{fn();results.push({name,passed:true});}catch(error){results.push({name,passed:false,message:error.message});throw error;}}
const plain=x=>JSON.parse(JSON.stringify(x));
const timeline=(text,s=0,e=4)=>api.buildWordTimeline({en:text,s,e});
try{
test('contractions, dotted abbreviations, clocks, money and percent share stable token boundaries',()=>assert.deepEqual(plain(api.speechTokens("I'm in the U.S. at 10:30. It costs $1,200.50 or 25%.").map(t=>t.text)),["I'm",'in','the','U.S.','at','10:30','It','costs','$1,200.50','or','25%']));
test('hyphens and accented names are not broken into unrelated timing nodes',()=>assert.deepEqual(plain(api.speechTokens('Jean-Pierre met José about COVID-19.').map(t=>t.text)),['Jean-Pierre','met','José','about','COVID-19']));
test('multisyllable content words receive more speech time than reduced articles',()=>assert(api.speechWeight('international')>api.speechWeight('the')*3));
test('spoken digits and initialisms receive pronunciation-aware weights',()=>assert(api.speechWeight('2026')>api.speechWeight('6')&&api.speechWeight('BBC')>api.speechWeight('a')*2));
test('common contractions and silent endings have sensible syllable counts',()=>assert.deepEqual([api.englishSyllables("I'm"),api.englishSyllables("isn't"),api.englishSyllables('walked'),api.englishSyllables('wanted')],[1,2,1,2]));
test('comma produces an unhighlighted pause instead of extending the preceding word',()=>{const t=timeline('Wait, listen.');assert(t.starts[1]>t.ends[0]);assert.equal(api.timelineWordIndex(t,(t.ends[0]+t.starts[1])/2),-1);});
test('title abbreviations do not create a false sentence pause',()=>{const t=timeline('Dr. Smith arrived.');assert(Math.abs(t.starts[1]-t.ends[0])<1e-8);});
test('terminal punctuation clears the final underline before the end of the caption',()=>{const t=timeline('A final word.');assert(t.ends.at(-1)<4);assert.equal(api.timelineWordIndex(t,4),-1);});
test('before-start and after-end times never select a word',()=>{const t=timeline('Hello world.',2,5);assert.equal(api.timelineWordIndex(t,1.99),-1);assert.equal(api.timelineWordIndex(t,5.01),-1);});
test('empty text and zero-duration cues remain safe',()=>{assert.equal(timeline('—').starts.length,0);assert.equal(api.buildWordTimeline({en:'test',s:1,e:1}).starts.length,0);});
test('tiny cue durations keep all word intervals finite and ordered',()=>{const t=timeline('A very short test, indeed.',0,.01);assert(t.starts.every((v,i)=>Number.isFinite(v)&&v<=t.ends[i]&&(!i||v>=t.ends[i-1]-1e-9)));assert(t.ends.at(-1)<=.01000001);});
test('legacy bilingual SRT still parses both language lines',()=>{const c=api.parseSRT('1\n00:00:01,000 --> 00:00:03,000\nHello world.\n你好，世界。')[0];assert.equal(c.en,'Hello world.');assert.equal(c.zh,'你好，世界。');assert.equal(c.s,1);});
test('WebVTT short clock format, cue IDs and settings are accepted',()=>{const c=api.parseSRT('WEBVTT\n\nintro-one\n00:01.250 --> 00:04.500 align:start\nHello.')[0];assert.equal(c.s,1.25);assert.equal(c.e,4.5);});
test('WebVTT comments and style blocks do not become spoken captions',()=>assert.equal(api.parseSRT('WEBVTT\n\nNOTE hidden\nnot a cue\n\nSTYLE\n::cue {color:red}\n\n00:00.000 --> 00:01.000\nA').length,1));
test('invalid or reversed caption clocks are skipped safely',()=>assert.equal(api.parseSRT('00:99.000 --> 01:02.000\nBad\n\n00:03.000 --> 00:02.000\nAlso bad').length,0));
test('out-of-order captions are sorted without rewriting original word times',()=>{const c=api.parseSRT('00:03.000 --> 00:04.000\nB\n\n00:01.000 --> 00:02.000\nA');assert.deepEqual(plain(c.map(x=>x.s)),[1,3]);});
const precise=api.parseSRT('WEBVTT\n\nvoice-a\n00:01.000 --> 00:04.000\n<v Alice><00:01.200>Hello <b><00:02.000>world</b><00:03.400>\n你好，世界。')[0];
test('inline timestamps and markup become plain text plus separate anchors',()=>{assert.equal(precise.en,'Hello world');assert.equal(precise.wordAnchors.length,3);assert.equal(precise.wordAnchors[1].at,6);});
test('fully timed VTT boundaries override all heuristic duration weights',()=>{const t=api.buildWordTimeline(precise);assert.equal(t.source,'timed');assert.deepEqual(plain(t.starts),[1.2,2]);assert.deepEqual(plain(t.ends),[2,3.4]);});
test('timed VTT keeps real leading and trailing silence unhighlighted',()=>{const t=api.buildWordTimeline(precise);assert.equal(api.timelineWordIndex(t,1.1),-1);assert.equal(api.timelineWordIndex(t,2.1),1);assert.equal(api.timelineWordIndex(t,3.5),-1);});
test('partial VTT anchors constrain the surrounding estimates',()=>{const c=api.parseSRT('00:00.000 --> 00:08.000\none <00:02.000>two three <00:05.000>four')[0],t=api.buildWordTimeline(c);assert.equal(t.source,'anchored');assert.equal(t.starts[1],2);assert.equal(t.starts[3],5);assert(t.ends[2]<=5);});
test('reversed inline timestamps fall back to estimates without dropping visible text',()=>{const c=api.parseSRT('00:00.000 --> 00:04.000\n<00:02.000>Hello <00:01.000>world')[0];assert.equal(c.en,'Hello world');assert.equal(api.buildWordTimeline(c).source,'estimated');});
test('out-of-range inline timestamps cannot select words outside the caption',()=>{const c=api.parseSRT('00:01.000 --> 00:04.000\n<00:05.000>Hello')[0];assert.equal(api.buildWordTimeline(c).source,'estimated');});
test('timestamps inside a lexical word do not split its lookup or highlight node',()=>{const c=api.parseSRT('00:01.000 --> 00:04.000\ninter<00:02.000>national')[0],t=api.buildWordTimeline(c);assert.equal(t.tokens.length,1);assert.equal(t.tokens[0].text,'international');});
test('entities are decoded as text while tags and annotation names are removed',()=>{const c=api.parseSRT('00:00.000 --> 00:02.000\n<v Bob>Fish &amp; chips &lt;safe&gt;')[0];assert.equal(c.en,'Fish & chips <safe>');});
test('invalid anchor metadata restored from a file cannot create missing timing entries',()=>{const t=api.buildWordTimeline({s:0,e:3,en:'hello world',wordAnchors:[{at:6,s:0}]});assert.equal(t.source,'estimated');assert.equal(t.starts.length,2);});
test('many anchors build complete monotonic timelines with binary lookup',()=>{const words=Array.from({length:3000},(_,i)=>'word'+i),en=words.join(' ');let at=0;const anchors=words.map((w,i)=>{const a={at,s:i*.2};at+=w.length+1;return a;});const t=api.buildWordTimeline({s:0,e:600,en,wordAnchors:anchors});assert.equal(t.starts.length,3000);assert.equal(api.timelineWordIndex(t,599.81),2999);});
test('binary search agrees with a linear reference across both words and pauses',()=>{const t=timeline('First, wait; then listen carefully.',0,5);for(let x=-.1;x<=5.1;x+=.007){const expected=t.starts.findIndex((v,i)=>x>=v&&x<t.ends[i]);assert.equal(api.timelineWordIndex(t,x),expected);}});
test('repeated tokenization does not leak regex scan state',()=>{assert.equal(api.speechTokens('One two').length,2);assert.equal(api.speechTokens('A').length,1);assert.equal(api.speechTokens('One two').length,2);});
}finally{
fs.writeFileSync(outputPath,JSON.stringify(results,null,2));
console.log(JSON.stringify({passed:results.filter(r=>r.passed).length,total:results.length,failures:results.filter(r=>!r.passed)},null,2));
}
