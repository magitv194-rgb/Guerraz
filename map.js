/* Cartografía vectorial original: polígonos territoriales esquemáticos del teatro sur. */
(function(){
"use strict";
const regions=[
{id:"northwest",name:"Altiplano",owner:"neutral",terrain:"Montaña",points:[[.34,.08],[.49,.10],[.54,.21],[.47,.29],[.35,.25],[.29,.16]],neighbors:["andes","north"]},
{id:"north",name:"Chaco Norte",owner:"neutral",terrain:"Bosque",points:[[.49,.10],[.66,.13],[.72,.25],[.62,.34],[.50,.29],[.54,.21]],neighbors:["northwest","center","east"]},
{id:"andes",name:"Cordillera",owner:"player",terrain:"Montaña",points:[[.29,.16],[.35,.25],[.36,.42],[.31,.55],[.24,.48],[.23,.30]],neighbors:["northwest","center","patagonia"]},
{id:"center",name:"Córdoba",owner:"player",terrain:"Llanura",points:[[.36,.25],[.50,.29],[.62,.34],[.60,.48],[.48,.53],[.36,.42]],neighbors:["andes","north","east","capital","west"]},
{id:"east",name:"Litoral",owner:"neutral",terrain:"Río",points:[[.62,.34],[.72,.25],[.80,.35],[.77,.51],[.66,.56],[.60,.48]],neighbors:["north","center","capital","coast"]},
{id:"west",name:"Cuyo",owner:"player",terrain:"Secano",points:[[.24,.48],[.31,.55],[.36,.42],[.48,.53],[.47,.67],[.35,.73],[.25,.65]],neighbors:["andes","center","capital","patagonia"]},
{id:"capital",name:"Distrito Capital",owner:"player",terrain:"Urbano",points:[[.48,.53],[.60,.48],[.66,.56],[.63,.67],[.53,.70],[.47,.67]],neighbors:["center","east","west","coast","south"]},
{id:"coast",name:"Costa Atlántica",owner:"neutral",terrain:"Costero",points:[[.80,.35],[.88,.45],[.84,.62],[.75,.73],[.63,.67],[.66,.56],[.77,.51]],neighbors:["east","capital","south"]},
{id:"patagonia",name:"Patagonia Norte",owner:"neutral",terrain:"Estepa",points:[[.25,.65],[.35,.73],[.47,.67],[.53,.70],[.54,.83],[.44,.90],[.30,.84],[.21,.75]],neighbors:["west","south","andes"]},
{id:"south",name:"Patagonia Sur",owner:"neutral",terrain:"Estepa",points:[[.53,.70],[.63,.67],[.75,.73],[.72,.88],[.61,.95],[.54,.83]],neighbors:["capital","coast","patagonia"]},
{id:"islands",name:"Archipiélago Austral",owner:"neutral",terrain:"Insular",points:[[.73,.91],[.82,.90],[.87,.96],[.79,.99],[.70,.97]],neighbors:["south"]},
{id:"border",name:"Frontera Norte",owner:"enemy",terrain:"Colinas",points:[[.66,.13],[.79,.17],[.84,.29],[.80,.35],[.72,.25]],neighbors:["north","east"]}
];
let canvas,ctx,view={scale:1,ox:0,oy:0},selected="capital",drag=null,lastState=null;
function resize(){const rect=canvas.getBoundingClientRect(),dpr=window.devicePixelRatio||1;canvas.width=Math.round(rect.width*dpr);canvas.height=Math.round(rect.height*dpr);ctx.setTransform(dpr,0,0,dpr,0,0);draw(lastState||{});}
function project(pt,w,h){const mapW=w*.78,mapH=h*.88,baseX=(w-mapW)/2,baseY=(h-mapH)/2;return{x:baseX+pt[0]*mapW,y:baseY+pt[1]*mapH};}
function polygonPath(r,w,h){const p=r.points.map(q=>project(q,w,h));const path=new Path2D();path.moveTo(p[0].x,p[0].y);p.slice(1).forEach(q=>path.lineTo(q.x,q.y));path.closePath();return path;}
function regionColor(r){if(r.owner==="player")return "#536f50";if(r.owner==="enemy")return "#895247";return "#77745a";}
function draw(state){
 if(!canvas)return;lastState=state;const w=canvas.clientWidth,h=canvas.clientHeight;ctx.clearRect(0,0,w,h);
 ctx.save();ctx.translate(view.ox,view.oy);ctx.translate(w/2,h/2);ctx.scale(view.scale,view.scale);ctx.translate(-w/2,-h/2);
 ctx.fillStyle="#26362c";ctx.fillRect(0,0,w,h);
 // subtle topographic contour texture
 for(let i=0;i<13;i++){ctx.beginPath();ctx.strokeStyle="rgba(190,190,145,.07)";ctx.lineWidth=1;for(let x=0;x<=w;x+=8){const y=h*(.12+i*.065)+Math.sin(x*.012+i)*11+Math.sin(x*.026+i*2)*5;if(x===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);}ctx.stroke();}
 // coastline silhouette and provinces
 regions.forEach(r=>{const path=polygonPath(r,w,h);ctx.fillStyle=regionColor(r);ctx.fill(path);ctx.strokeStyle="#b6b99a";ctx.lineWidth=1.4;ctx.stroke(path);if(r.id===selected){ctx.strokeStyle="#f0c96e";ctx.lineWidth=3;ctx.stroke(path);}});
 // terrain marks
 regions.forEach(r=>{const c=centroid(r,w,h);ctx.fillStyle="rgba(18,25,19,.23)";ctx.font="10px Georgia";ctx.textAlign="center";let mark=r.terrain==="Montaña"?"▲  ▲  ▲":r.terrain==="Bosque"?"♣  ♣  ♣":r.terrain==="Río"?"〰  〰":r.terrain==="Costero"?"≈  ≈  ≈":"·  ·  ·";ctx.fillText(mark,c.x,c.y+18);});
 // unit march paths
 (state.units||[]).forEach(u=>{if(u.moving){const a=regionById(u.from),b=regionById(u.to);if(a&&b){const ca=centroid(a,w,h),cb=centroid(b,w,h);ctx.beginPath();ctx.setLineDash([5,4]);ctx.strokeStyle="#e2c16c";ctx.lineWidth=2;ctx.moveTo(ca.x,ca.y);ctx.lineTo(cb.x,cb.y);ctx.stroke();ctx.setLineDash([]);const t=Math.max(0,Math.min(1,(Date.now()-u.startedAt)/(u.arrivesAt-u.startedAt)));drawArrow(ca,cb,ctx);}}});
 // labels
 regions.forEach(r=>{const c=centroid(r,w,h);ctx.textAlign="center";ctx.fillStyle="#f1edda";ctx.font="bold 10px Arial";ctx.shadowColor="#182018";ctx.shadowBlur=4;ctx.fillText(r.name.toUpperCase(),c.x,c.y-2);ctx.shadowBlur=0;});
 // army icons rendered above labels
 (state.units||[]).forEach(u=>{const loc=regionById(u.moving?u.from:u.province);if(!loc)return;const c=centroid(loc,w,h);let x=c.x,y=c.y+35;if(u.moving){const dest=regionById(u.to);if(dest){const d=centroid(dest,w,h),t=Math.max(0,Math.min(1,(Date.now()-u.startedAt)/(u.arrivesAt-u.startedAt)));x=c.x+(d.x-c.x)*t;y=c.y+35+(d.y-c.y)*t;}}ctx.beginPath();ctx.fillStyle="#172019";ctx.strokeStyle="#d8c17e";ctx.lineWidth=1.3;ctx.arc(x,y,13,0,Math.PI*2);ctx.fill();ctx.stroke();ctx.fillStyle="#f1e6c8";ctx.font="bold 12px Arial";ctx.textAlign="center";ctx.fillText(u.type==="tank"?"T":u.type==="artillery"?"A":u.type==="infantry"?"I":u.type==="fighter"?"✈":"N",x,y+4);ctx.fillStyle="#fff";ctx.font="8px Arial";ctx.fillText(String(u.count),x+12,y-9);});
 ctx.restore();
}
function centroid(r,w,h){let x=0,y=0;r.points.forEach(p=>{const q=project(p,w,h);x+=q.x;y+=q.y;});return{x:x/r.points.length,y:y/r.points.length};}
function regionById(id){return regions.find(r=>r.id===id);}
function drawArrow(a,b,c){const t=.55,x=a.x+(b.x-a.x)*t,y=a.y+(b.y-a.y)*t,ang=Math.atan2(b.y-a.y,b.x-a.x);c.save();c.translate(x,y);c.rotate(ang);c.fillStyle="#f0c76a";c.beginPath();c.moveTo(8,0);c.lineTo(-5,-5);c.lineTo(-5,5);c.closePath();c.fill();c.restore();}
function hitTest(x,y){const w=canvas.clientWidth,h=canvas.clientHeight;const px=(x-view.ox-w/2)/view.scale+w/2,py=(y-view.oy-h/2)/view.scale+h/2;for(let i=regions.length-1;i>=0;i--){const r=regions[i],p=r.points.map(q=>project(q,w,h));let inside=false;for(let a=0,b=p.length-1;a<p.length;b=a++){if(((p[a].y>py)!==(p[b].y>py))&&(px<(p[b].x-p[a].x)*(py-p[a].y)/(p[b].y-p[a].y)+p[a].x))inside=!inside;}if(inside)return r.id;}return null;}
function init(){canvas=document.getElementById("warMap");ctx=canvas.getContext("2d");window.addEventListener("resize",resize);canvas.addEventListener("click",e=>{if(drag&&drag.moved){drag=null;return;}const r=canvas.getBoundingClientRect(),id=hitTest(e.clientX-r.left,e.clientY-r.top);if(id){selected=id;window.dispatchEvent(new CustomEvent("province-selected",{detail:id}));draw(lastState||{});}});canvas.addEventListener("pointerdown",e=>{drag={x:e.clientX,y:e.clientY,ox:view.ox,oy:view.oy,moved:false};canvas.setPointerCapture(e.pointerId);});canvas.addEventListener("pointermove",e=>{if(drag){const dx=e.clientX-drag.x,dy=e.clientY-drag.y;if(Math.abs(dx)+Math.abs(dy)>5)drag.moved=true;if(drag.moved){view.ox=drag.ox+dx;view.oy=drag.oy+dy;draw(lastState||{});}}});canvas.addEventListener("pointerup",()=>{if(drag&&!drag.moved)drag=null;else drag=null;});document.getElementById("zoomIn").onclick=()=>{view.scale=Math.min(2.2,view.scale*1.15);draw(lastState||{});};document.getElementById("zoomOut").onclick=()=>{view.scale=Math.max(.8,view.scale/1.15);draw(lastState||{});};document.getElementById("centerMap").onclick=()=>{view={scale:1,ox:0,oy:0};draw(lastState||{});};resize();}
window.MapRenderer={regions,regionById,draw,init,getSelected:()=>selected,setSelected:id=>{selected=id;draw(lastState||{});}};
})();