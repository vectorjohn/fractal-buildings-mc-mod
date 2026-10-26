const fs=require('fs');const dir=process.argv[2];
const S=JSON.parse(fs.readFileSync(dir+'/settlement.json'));
const B=S.bounds,W=B.w,H=B.d,X0=B.x,Z0=B.z;const [cx,cz]=S.site.centre,R=S.site.radius;
const g=new Uint8Array(W*H);// 0 none,1 road,2 lot,3 plaza,4 steepEmpty lot
const C=S.coarse;
function water(x,z){const i=Math.floor((x-C.x0)/C.step),j=Math.floor((z-C.z0)/C.step);if(i<0||j<0||i>=C.nx||j>=C.nz)return true;return C.water[j*C.nx+i]>0;}
function urban(x,z){const i=Math.floor((x-C.x0)/C.step),j=Math.floor((z-C.z0)/C.step);if(i<0||j<0||i>=C.nx||j>=C.nz)return false;return C.mask[j*C.nx+i]>0;}
const K=new Uint8Array(W*H);let curK=0;const KN=["","arterial","main","street","lane","quay","road"];function set(x,z,v){if(v==1){const i0=x-X0,j0=z-Z0;if(i0>=0&&j0>=0&&i0<W&&j0<H&&!K[j0*W+i0])K[j0*W+i0]=curK;}const i=x-X0,j=z-Z0;if(i<0||j<0||i>=W||j>=H)return;const k=j*W+i;if(v==1||g[k]==0)g[k]=v;}
const streets=[];let lenByKind={};
function addLine(pts,w,kind,id,dist){curK=Math.max(1,KN.indexOf(kind));for(let s=0;s<pts.length-1;s++){const[a,b]=pts[s],[c,d]=pts[s+1];const L=Math.hypot(c-a,d-b);lenByKind[kind]=(lenByKind[kind]||0)+L;const n=Math.max(1,Math.ceil(L));const h=Math.floor(w/2);for(let t=0;t<=n;t++){const x=Math.round(a+(c-a)*t/n),z=Math.round(b+(d-b)*t/n);for(let u=-h;u<=w-1-h;u++)for(let v=-h;v<=w-1-h;v++)set(x+u,z+v,1);}}streets.push({pts,w,kind,id,dist});}
const lots=[];let plazas=[];
for(const d of S.districts){if(!d.detail)continue;let D;try{D=JSON.parse(fs.readFileSync(dir+'/'+d.detail));}catch(e){continue;}
 for(const s of D.streets||[])addLine(s.points,s.width||5,s.kind,s.id,d.id);
 for(const l of D.lots||[]){lots.push(l);}
 for(const p of D.plazas||[])plazas.push(p);}
for(const r of S.roads||[]){/* settlement roads also present as district arterials; rasterize too */ const pts=r.points;for(let s=0;s<pts.length-1;s++){const[a,b]=pts[s],[c,d]=pts[s+1];const L=Math.hypot(c-a,d-b),n=Math.ceil(L),w=r.width,h=Math.floor(w/2);for(let t=0;t<=n;t++){const x=Math.round(a+(c-a)*t/n),z=Math.round(b+(d-b)*t/n);for(let u=-h;u<=w-1-h;u++)for(let v=-h;v<=w-1-h;v++)set(x+u,z+v,1);}}}
for(const l of lots){const v=l.status=='build'?2:4;for(let x=l.x;x<l.x+l.w;x++)for(let z=l.z;z<l.z+l.d;z++)set(x,z,v);}
for(const p of plazas)for(let x=p.x;x<p.x+p.w;x++)for(let z=p.z;z<p.z+p.d;z++)set(x,z,3);
// precompute land mask in disk
const rings=[0,0.25,0.5,0.75,1.0001];const acc=rings.slice(0,-1).map(()=>({land:0,urb:0,road:0,lot:0,plaza:0,steep:0}));
const inLand=new Uint8Array(W*H);
for(let j=0;j<H;j++)for(let i=0;i<W;i++){const x=X0+i,z=Z0+j;const r=Math.hypot(x-cx,z-cz)/R;if(r>1)continue;if(water(x,z))continue;inLand[j*W+i]=1;let k=0;while(r>=rings[k+1])k++;const a=acc[k];a.land++;if(urban(x,z))a.urb++;const v=g[j*W+i];if(v==1)a.road++;else if(v==2)a.lot++;else if(v==3)a.plaza++;else if(v==4)a.steep++;}
const pc=(a,b)=>(100*a/b).toFixed(1);
console.log('site',S.site.id,S.site.tier,'r',R,'lots',lots.length,'built',lots.filter(l=>l.status=='build').length,'streets',streets.length);
console.log('ring  land(kb2) urban% road% lot% plaza% steepLot% empty%');
let T={land:0,urb:0,road:0,lot:0,plaza:0,steep:0};
acc.forEach((a,k)=>{for(const q in T)T[q]+=a[q];console.log(rings[k]+'-'+rings[k+1].toFixed(2),(a.land/1000).toFixed(0),pc(a.urb,a.land),pc(a.road,a.land),pc(a.lot,a.land),pc(a.plaza,a.land),pc(a.steep,a.land),pc(a.land-a.road-a.lot-a.plaza-a.steep,a.land));});
const a=T;console.log('ALL',(a.land/1000).toFixed(0),pc(a.urb,a.land),pc(a.road,a.land),pc(a.lot,a.land),pc(a.plaza,a.land),pc(a.steep,a.land),pc(a.land-a.road-a.lot-a.plaza-a.steep,a.land));
// lot/road ratio on urban land
console.log('street length by kind (blocks):',JSON.stringify(Object.fromEntries(Object.entries(lenByKind).map(([k,v])=>[k,Math.round(v)]))));
const landKm2=a.land/1e6;console.log('centreline street length per km2 of land:',Math.round(Object.values(lenByKind).reduce((x,y)=>x+y,0)/landKm2),'m/km2');
// blocks: components of non-road land
const lab=new Int32Array(W*H);let nb=0;const blocks=[];const st=new Int32Array(W*H);
for(let k0=0;k0<W*H;k0++){if(!inLand[k0]||g[k0]==1||lab[k0])continue;nb++;let sp=0;st[sp++]=k0;lab[k0]=nb;let area=0,lot=0,edge=false;let minx=1e9,maxx=-1e9,minz=1e9,maxz=-1e9;
 while(sp){const k=st[--sp];area++;if(g[k]==2)lot++;const i=k%W,j=(k-i)/W;if(i<minx)minx=i;if(i>maxx)maxx=i;if(j<minz)minz=j;if(j>maxz)maxz=j;
  for(const[di,dj]of[[1,0],[-1,0],[0,1],[0,-1]]){const ii=i+di,jj=j+dj;if(ii<0||jj<0||ii>=W||jj>=H){edge=true;continue;}const kk=jj*W+ii;if(!inLand[kk]){edge=true;continue;}if(g[kk]==1||lab[kk])continue;lab[kk]=nb;st[sp++]=kk;}}
 const r=Math.hypot(X0+(minx+maxx)/2-cx,Z0+(minz+maxz)/2-cz)/R;blocks.push({area,lot,edge,w:maxx-minx+1,d:maxz-minz+1,r});}
const inner=blocks.filter(b=>!b.edge);
function q(arr,p){if(!arr.length)return 0;const s=arr.slice().sort((x,y)=>x-y);return s[Math.floor(p*(s.length-1))];}
for(const [lo,hi] of [[0,0.5],[0.5,1.01]]){const bs=inner.filter(b=>b.r>=lo&&b.r<hi&&b.area>=64);const ar=bs.map(b=>b.area);const tot=ar.reduce((x,y)=>x+y,0),lt=bs.reduce((x,b)=>x+b.lot,0);
 const empty=bs.filter(b=>b.lot==0);const sl=bs.filter(b=>Math.min(b.w,b.d)<=16);
 console.log(`enclosed blocks r ${lo}-${hi}: n ${bs.length}, area p25/50/75 ${q(ar,.25)}/${q(ar,.5)}/${q(ar,.75)}, short side median ${q(bs.map(b=>Math.min(b.w,b.d)),.5)}, long median ${q(bs.map(b=>Math.max(b.w,b.d)),.5)}, lot cover of block area ${pc(lt,tot)}%, blocks with no lot ${empty.length} (${pc(empty.reduce((x,b)=>x+b.area,0),tot)}% of area), sliver blocks short<=16 ${sl.length}`);}
// gap scan: between two road cells on a row/col, gaps with no lot
let gaps=[];for(const dirn of [0,1]){const NA=dirn?W:H,NB=dirn?H:W;for(let a1=0;a1<NA;a1+=4){let last=-1,hasLot=false,ok=true;for(let b1=0;b1<NB;b1++){const k=dirn?(b1*W+a1):(a1*W+b1);if(!inLand[k]){last=-1;continue;}if(g[k]==1){if(last>=0&&b1-last>1)gaps.push({len:b1-last-1,lot:hasLot});last=b1;hasLot=false;}else if(g[k]==2)hasLot=true;}}}
const gl=gaps.filter(x=>!x.lot);const gs=gaps.filter(x=>x.lot);
console.log(`road-to-road gaps (rows/cols every 4): n ${gaps.length}; with lots median ${q(gs.map(x=>x.len),.5)}; without lots n ${gl.length} (${pc(gl.length,gaps.length)}%), median ${q(gl.map(x=>x.len),.5)}; empty gaps <=12 (sliver between parallel roads) ${pc(gl.filter(x=>x.len<=12).length,gaps.length)}% of all gaps`);
// dead ends
function segd(px,pz,a,b,c,d){const vx=c-a,vz=d-b,L2=vx*vx+vz*vz;let t=L2?((px-a)*vx+(pz-b)*vz)/L2:0;t=Math.max(0,Math.min(1,t));return Math.hypot(px-(a+t*vx),pz-(b+t*vz));}
let dead=0,deadK={};const near=(p,self)=>{for(const s of streets){if(s===self)continue;for(let i=0;i<s.pts.length-1;i++){if(segd(p[0],p[1],s.pts[i][0],s.pts[i][1],s.pts[i+1][0],s.pts[i+1][1])<=s.w/2+1.5)return true;}}return false;};
for(const s of streets){for(const p of [s.pts[0],s.pts[s.pts.length-1]]){if(!inLand[(p[1]-Z0)*W+(p[0]-X0)])continue;if(!near(p,s)){dead++;deadK[s.kind]=(deadK[s.kind]||0)+1;}}}
console.log('dead-end street ends (no other street within width/2+1.5):',dead,JSON.stringify(deadK));
// lot types by ring
const tb={};for(const l of lots){if(l.status!='build')continue;const r=Math.hypot(l.x+l.w/2-cx,l.z+l.d/2-cz)/R;const k=r<.25?0:r<.5?1:r<.75?2:3;tb[l.type]=tb[l.type]||[0,0,0,0];tb[l.type][k]++;}
console.log('built lot types by ring:',JSON.stringify(tb));
console.log('lot size median',q(lots.map(l=>l.w*l.d),.5),'highrise lot median',q(lots.filter(l=>l.type=='highrise').map(l=>l.w*l.d),.5));
const pairs={};for(const dirn of [0,1]){const NA=dirn?W:H,NB=dirn?H:W;for(let a1=0;a1<NA;a1+=4){let last=-1,lk=0,hasLot=false;for(let b1=0;b1<NB;b1++){const k=dirn?(b1*W+a1):(a1*W+b1);if(!inLand[k]){last=-1;continue;}if(g[k]==1){if(last>=0&&b1-last>1&&b1-last-1<=12&&!hasLot){const key=[KN[lk]||'road',KN[K[k]]||'road'].sort().join('+');pairs[key]=(pairs[key]||0)+1;}last=b1;lk=K[k];hasLot=false;}else if(g[k]==2)hasLot=true;}}}
console.log('empty slivers <=12 by bounding road kinds:',JSON.stringify(Object.entries(pairs).sort((a,b)=>b[1]-a[1])));
const fr=[[0,0],[0,0],[0,0],[0,0]];const frk={};
for(let j=1;j<H-1;j++)for(let i=1;i<W-1;i++){const k=j*W+i;if(!inLand[k]||g[k]==1)continue;let edge=false,kk=0;for(const d of [1,-1,W,-W])if(g[k+d]==1){edge=true;kk=K[k+d];}if(!edge)continue;const r=Math.hypot(X0+i-cx,Z0+j-cz)/R;const b=r<.25?0:r<.5?1:r<.75?2:3;fr[b][1]++;const has=g[k]==2||g[k]==3;if(has)fr[b][0]++;const n=KN[kk]||'road';frk[n]=frk[n]||[0,0];frk[n][1]++;if(has)frk[n][0]++;}
console.log('street-edge frontage occupied by lots/plazas by ring:',fr.map(x=>pc(x[0],x[1])+'%').join(' '),' by kind:',JSON.stringify(Object.fromEntries(Object.entries(frk).map(([k,v])=>[k,pc(v[0],v[1])+'% of '+v[1]]))));
