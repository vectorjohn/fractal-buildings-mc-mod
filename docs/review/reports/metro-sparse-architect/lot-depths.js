const fs=require('fs');const dir=process.argv[2];const S=JSON.parse(fs.readFileSync(dir+'/settlement.json'));const[cx,cz]=S.site.centre,R=S.site.radius;
const by={};let tilesLand=0,tilesEmpty=0;
for(const d of S.districts){let D;try{D=JSON.parse(fs.readFileSync(dir+'/'+d.detail))}catch(e){continue}
 const r=Math.hypot(d.x+d.w/2-cx,d.z+d.d/2-cz)/R; if(r>1)continue; const urb=(D.streets||[]).length>0; if(urb){tilesLand++; if(!(D.lots||[]).some(l=>l.status=='build'))tilesEmpty++;}
 for(const l of D.lots||[]){if(l.status!='build')continue;const dep=(l.front=='north'||l.front=='south')?l.d:l.w;const wid=(l.front=='north'||l.front=='south')?l.w:l.d;const k=l.type+(r<0.5?'-core':'-outer');(by[k]=by[k]||[]).push([dep,wid]);}}
const q=(a,p)=>{a=a.slice().sort((x,y)=>x-y);return a[Math.floor(p*(a.length-1))]};
for(const k of Object.keys(by).sort()){const a=by[k];console.log(k.padEnd(18),'n',a.length,'depth p50/p90',q(a.map(x=>x[0]),.5),q(a.map(x=>x[0]),.9),'frontage p50',q(a.map(x=>x[1]),.5));}
console.log('tiles with streets',tilesLand,'of which no built lot',tilesEmpty);
