// Compare actual app timing with the 1.16 square-root length baseline.
const fs=require('fs'),path=require('path'),vm=require('vm');
const source=process.argv[2]||path.resolve(__dirname,'../assets/index.html');
const reference=process.argv[3]||path.join(__dirname,'word-reference.json');
const output=process.argv[4]||path.join(__dirname,'word-benchmark-results.json');
const html=fs.readFileSync(source,'utf8'),start=html.indexOf('/* 词形、发音时长'),end=html.indexOf('/* 仅在字幕内容完全对应',start);
if(start<0||end<start)throw new Error('App timing block was not found');
const core=vm.runInNewContext('const CJK=/[\\u4e00-\\u9fff\\u3400-\\u4dbf]/;\n'+html.slice(start,end)+'\n;({buildWordTimeline})');
const rows=JSON.parse(fs.readFileSync(reference,'utf8'));
function oldStarts(text,s,e){
 const tokens=[...text.matchAll(/[A-Za-z]+(?:['’\-][A-Za-z]+)*|\d+(?:[.,]\d+)*/g)].map(m=>({at:m.index,end:m.index+m[0].length,text:m[0]}));
 let total=0;const weights=tokens.map((t,i)=>{const after=text.slice(t.end,tokens[i+1]?.at??text.length);const w=Math.sqrt(Math.max(1,t.text.replace(/[^A-Za-z0-9]/g,'').length))+(/^[,;:.!?]/.test(after)?.5:0);total+=w;return w;});
 let cursor=s;return tokens.map((t,i)=>{const at=cursor;cursor+=(e-s)*weights[i]/total;return {...t,start:at};});
}
function summary(values){const a=[...values].sort((a,b)=>a-b);return {n:a.length,meanMs:Math.round(a.reduce((a,b)=>a+b,0)/a.length*1000),medianMs:Math.round(a[Math.floor(a.length*.5)]*1000),p90Ms:Math.round(a[Math.floor(a.length*.9)]*1000)};}
const result={voice:'Microsoft Zira Desktop',format:'16 kHz mono PCM',scope:'Synthetic speech word onsets; not human recording accuracy.',groups:{}};
for(const split of ['development','holdout']){
 result.groups[split]={sentences:rows.filter(r=>r.split===split).length,scenarios:{}};
 for(const scenario of ['tight','padded']){
  const old=[],next=[];
  for(const r of rows.filter(r=>r.split===split)){
   const s=scenario==='tight'?r.words[0].start:0,e=scenario==='tight'?Math.min(r.duration,r.speechEnd+.035):r.duration;
   const timeline=core.buildWordTimeline({en:r.text,s,e}),baseline=oldStarts(r.text,s,e);
   timeline.tokens.forEach((t,i)=>{
    const references=r.words.filter(w=>w.at<t.end&&w.at+w.length>t.at),before=baseline.find(w=>w.at<t.end&&w.end>t.at);
    if(!references.length||!before)return;
    const truth=Math.min(...references.map(w=>w.start));old.push(Math.abs(before.start-truth));next.push(Math.abs(timeline.starts[i]-truth));
   });
  }
  result.groups[split].scenarios[scenario]={old:summary(old),next:summary(next)};
 }
}
fs.writeFileSync(output,JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
