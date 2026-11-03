const fs = require('fs');
const path = require('path');
const sharp = require('C:/Users/25756/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const root = path.resolve(__dirname, '..');
const out = path.join(root, 'previews');
fs.mkdirSync(out, {recursive:true});
const pages = fs.readdirSync(path.join(root,'svg_output')).filter(n=>n.endsWith('.svg')).sort();
function attr(s,k) {return s.match(new RegExp(k+'="([^"]*)"'))?.[1];}
(async()=>{
  const thumbs=[];
  for (const n of pages) {
    let svg=fs.readFileSync(path.join(root,'svg_output',n),'utf8');
    svg=svg.replace(/<use\b[^>]*data-icon="[^"]+"[^>]*\/?\s*>\s*(?:<\/use>)?/g,tag=>{
      const icon=fs.readFileSync(path.join(root,'icons',attr(tag,'data-icon')+'.svg'),'utf8');
      const content=icon.slice(icon.indexOf('>')+1,icon.lastIndexOf('</svg>'));
      const vb=attr(icon,'viewBox');
      return `<svg x="${attr(tag,'x')}" y="${attr(tag,'y')}" width="${attr(tag,'width')}" height="${attr(tag,'height')}" viewBox="${vb}">${content}</svg>`;
    });
    const target=path.join(out,n.replace('.svg','.png'));
    await sharp(Buffer.from(svg)).png().toFile(target);
    thumbs.push(await sharp(target).resize(640,360).png().toBuffer());
  }
  for(let b=0;b<2;b++) {
    const batch=thumbs.slice(b*7,b*7+7);
    await sharp({create:{width:1280,height:1440,channels:4,background:'#203440'}})
      .composite(batch.map((input,i)=>({input,left:(i%2)*640,top:Math.floor(i/2)*360})))
      .png().toFile(path.join(out,`contact-${b+1}.png`));
  }
  console.log(`Rendered ${pages.length} pages and 2 contact sheets.`);
})().catch(e=>{console.error(e);process.exit(1);});
