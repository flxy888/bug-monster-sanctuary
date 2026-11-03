import fs from 'node:fs/promises';
import { constants as fsConstants } from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { Presentation, PresentationFile } from 'file:///C:/Users/25756/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs';

const D=path.dirname(fileURLToPath(import.meta.url));
const ROOT=path.dirname(D);
const SKILL='C:/Users/25756/.codex/plugins/cache/openai-primary-runtime/presentations/26.1026.10807/skills/presentations';
const PY='C:/Users/25756/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';
const OUT=path.join(ROOT,'editable-output','Java课程作业展示_可编辑版.pptx');
const C={paper:'#FCFBF7',ink:'#2F3437',muted:'#60686B',blue:'#DCECF9',sage:'#DFEDDD',peach:'#F9DFD2',lav:'#E9DEF6',white:'#FFFFFF',border:'#60686B'};
const F={head:'KaiTi',body:'Microsoft YaHei',code:'Consolas'};
const p=Presentation.create({slideSize:{width:960,height:540}});
const speech=await fs.readFile(path.join(ROOT,'speech.md'),'utf8');
const noteBlocks=[...speech.matchAll(/^## Slide (\d+):[^\n]*\n([\s\S]*?)(?=^## Slide |(?![\s\S]))/gm)];
const notes=new Map(noteBlocks.map(m=>[Number(m[1]),m[2].trim()]));
if(notes.size!==12||[...notes.values()].some(v=>v.length<40))throw new Error('All 12 speaker-note blocks must be populated.');
const counts=[];
let seq=0;
function text(s,t,x,y,w,h,size=21,opt={}){
  const q=s.shapes.add({geometry:'textbox',name:opt.name||`text-${++seq}`,position:{left:x,top:y,width:w,height:h},fill:opt.fill||'none',line:{fill:'none',width:0}});
  q.text=t;
  q.text.style={fontSize:size,typeface:opt.font||F.body,color:opt.color||C.ink,bold:opt.bold||false,alignment:opt.align||'left',verticalAlignment:opt.vAlign||'top',wrap:'square',autoFit:'none',insets:opt.insets??0,lineSpacing:1.15};
  return q;
}
function box(s,t,x,y,w,h,fill=C.blue,size=22,opt={}){
  const q=s.shapes.add({geometry:opt.geometry||'roundRect',name:opt.name||`diagram-${++seq}`,position:{left:x,top:y,width:w,height:h},fill,line:{fill:opt.border||C.border,width:1.15},borderRadius:8});
  q.text=t;
  q.text.style={fontSize:size,typeface:opt.font||F.body,color:C.ink,alignment:opt.align||'center',verticalAlignment:'middle',autoFit:'none',wrap:'square',insets:opt.insets??8,bold:opt.bold||false,lineSpacing:1.15};
  return q;
}
function frame(s,x,y,w,h,fill='none'){
  return s.shapes.add({geometry:'roundRect',name:`region-${++seq}`,position:{left:x,top:y,width:w,height:h},fill,line:{fill:C.border,width:1.05},borderRadius:9});
}
function line(s,x,y,w,h=0,color=C.border,width=1.2){return s.shapes.add({geometry:'line',position:{left:x,top:y,width:w,height:h},fill:'none',line:{fill:color,width}});}
function conn(s,a,b,from='right',to='left',arrow=true,kind='straight',both=false){return s.shapes.connect(a,b,{kind,fromSide:from,toSide:to,line:{fill:C.ink,width:1.5},tail:{type:arrow?'arrow':'none',width:'med',length:'med'},head:{type:both?'arrow':'none'}});}
function slide(n,title,sub=''){
  const s=p.slides.add();s.background.fill=C.paper;
  if(title){text(s,title,32,24,902,54,36,{font:F.head,bold:true,name:`title-slide-${n}`});line(s,34,82,Math.min(450,title.length*21),0,C.blue,5);}
  if(sub)text(s,sub,36,94,888,42,20);
  s.speakerNotes.text=notes.get(n)||'';
  return s;
}
async function img(s,file,x,y,w,h,crop,alt){
  const b=await fs.readFile(path.join(D,'source_images',file));
  const im=s.images.add({blob:new Uint8Array(b),contentType:'image/png',fit:'contain',position:{left:x,top:y,width:w,height:h},alt});
  if(crop)im.crop=crop;
  return im;
}

// 1. Cover: editable copy and workflow; illustration is a separate cropped asset.
{
 const s=slide(1);
 text(s,'Bug 怪兽收容所',38,88,575,67,51,{font:F.head,bold:true});
 text(s,'Java 课程作业展示',44,172,540,50,34,{font:F.head});
 text(s,'基于 Spring Boot 与 JavaParser 的\n游戏化代码质量检测系统',44,251,535,78,24);
 await img(s,'slide_01.png',620,52,305,310,{left:.605,top:.055,right:.006,bottom:.28},'原版封面的独立怪兽插画，可移动、缩放或替换');
 const a=['检测问题','修改代码','验证收容','记录成长'].map((t,i)=>box(s,t,40+i*230,405,190,65,[C.blue,C.peach,C.lav,C.sage][i],25,{font:F.head}));
 for(let i=0;i<3;i++)conn(s,a[i],a[i+1]);
 text(s,'本机单用户课程项目',330,496,300,28,19,{align:'center'});
}
// 2. Architecture.
{
 const s=slide(2,'整体架构：从 Java 源码到收容反馈','浏览器负责交互，Java 后端负责判断与结算');
 const front=box(s,'编辑与展示\n\nVue 3 / Monaco',30,207,175,155,C.blue,23,{font:F.head});
 const back=frame(s,275,150,420,270);
 text(s,'Spring Boot 后端',310,164,350,36,28,{font:F.head,align:'center'});
 const an=[['CodeAnalyzer',294,219,117],['JavaParser\n/ AST',433,219,110],['五条规则\n与评分',570,219,107]].map(([t,x,y,w])=>box(s,t,x,y,w,60,C.peach,t.startsWith('五')?18:14,{font:t.startsWith('五')?F.body:F.code,insets:4}));
 conn(s,an[0],an[1]);conn(s,an[1],an[2]);
 const bu=[['ScanService',294,327,117],['IdentityMatcher',426,327,130],['修复与\n奖励判定',570,327,107]].map(([t,x,y,w])=>box(s,t,x,y,w,60,C.lav,t==='IdentityMatcher'?12.4:t.startsWith('修')?18:14,{font:t.startsWith('修')?F.body:F.code,insets:3}));
 conn(s,bu[0],bu[1]);conn(s,bu[1],bu[2]);conn(s,an[1],bu[1],'bottom','top');
 const db=box(s,'MyBatis / MySQL\n\n源码快照\n问题谱系\n奖励记录',765,205,167,176,C.sage,20);
 conn(s,front,back);text(s,'REST API',207,232,66,53,16,{font:F.code,align:'center'});
 conn(s,back,db,'right','left',true,'straight',true);
 const response=box(s,'评分 · 行号 · 收容结果 · XP',260,449,440,34,C.blue,19,{border:C.blue});
 conn(s,back,response,'bottom','top');conn(s,response,front,'left','bottom',true,'elbow');
 text(s,'用户源码仅作静态解析；XP 由后端事务统一结算',140,503,750,28,20,{align:'center'});
}
// 3. Backend package responsibilities.
{
 const s=slide(3,'Java 后端：按职责分层','让请求处理、业务判断与数据访问各司其职');
 const api=box(s,'api\nApiController：参数校验与 HTTP 响应',40,151,510,80,C.blue,23);
 const service=box(s,'service\nScanService / IdentityMatcher\n扫描编排与收容判定',40,276,510,110,C.sage,23);
 const analysis=box(s,'analysis\nCodeAnalyzer\nAnalysisRule\nAST 解析与规则检查',620,266,300,130,C.peach,19);
 const data=box(s,'persistence\nMapper / Rows：SQL 与数据映射',40,429,510,70,C.lav,22);
 conn(s,api,service,'bottom','top');conn(s,service,analysis,'right','left',true,'straight',true);conn(s,service,data,'bottom','top',true,'straight',true);
 text(s,'POST /api/projects/{id}/scans    →    ScanService.scan()',53,505,850,28,18,{font:F.code});
}
// 4. Editable interface extract and polymorphism diagram.
{
 const s=slide(4,'面向对象：一套接口，五条规则');
 text(s,'AnalysisRule 统一契约；Spring 注入 List<AnalysisRule>',36,97,888,30,21);
 text(s,'Stream 汇总问题，record 表达结果数据',36,134,888,30,21);
 frame(s,32,194,446,227);text(s,'AnalysisRule.java（真实源码片段）',48,205,415,34,20,{fill:C.blue});
 text(s,'public interface AnalysisRule {\n    String code();\n    List<AnalysisResult.Finding>\n        inspect(CompilationUnit unit);\n}',48,258,415,145,18,{font:F.code});
 const parent=box(s,'AnalysisRule',500,280,150,52,C.sage,19,{font:F.code});
 const names=['EmptyCatchRule','EmptyIfRule','LongMethodRule','DeepNestingRule','TooManyParametersRule'];
 const kids=names.map((v,i)=>box(s,v,700,195+i*50,230,38,[C.peach,C.blue,C.sage,C.lav,C.peach][i],14,{font:F.code,insets:3}));
 for(const k of kids)conn(s,parent,k,'right','left',false,'elbow');
 box(s,'各规则 inspect()  →  汇总 Finding  →  排序与评分',32,455,560,54,C.sage,19);
 text(s,'AnalysisResult / Finding\n使用 Java record',627,454,300,64,20,{font:F.code,align:'center'});
}
// 5. AST inspection example.
{
 const s=slide(5,'AST 如何识别空 catch','找到 CatchClause，判断 catch 代码块是否为空，再生成 Finding');
 const xs=[30,342,654];for(const x of xs)frame(s,x,160,278,280);
 ['示意代码片段','AST 关键节点','生成的 Finding'].forEach((t,i)=>text(s,t,xs[i]+12,172,254,34,25,{font:F.head,align:'center',fill:[C.blue,C.sage,C.lav][i]}));
 text(s,'try {\n    parseAmount();\n} catch (Exception e) {\n}',42,245,254,146,15.7,{font:F.code});
 const a=box(s,'TryStmt',411,228,140,38,C.blue,20,{font:F.code});
 const b=box(s,'CatchClause',391,301,180,38,C.blue,20,{font:F.code});
 const c=box(s,'BlockStmt（空）',384,374,194,40,C.peach,19,{font:F.code});
 conn(s,a,b,'bottom','top');conn(s,b,c,'bottom','top');
 text(s,'EMPTY_CATCH\n起止行号：3–4\n\n说明与修复建议',674,236,238,155,22,{font:F.body});
 text(s,'每次请求独立创建解析器，并显式设置 JAVA_21',40,463,880,30,20,{align:'center'});
 text(s,'解析失败返回 PARSE_ERROR，分数为空，不推进成功基线',40,502,880,28,19,{align:'center',color:C.muted});
}
// 6. Native editable rule table.
{
 const s=slide(6,'五条规则与可复算评分');
 const values=[['规则','判定条件','扣分'],['EMPTY_CATCH','空 catch','15 分'],['LONG_METHOD','方法体 > 30 行','10 分'],['DEEP_NESTING','控制结构达到 4 层','10 分'],['TOO_MANY_PARAMETERS','方法参数 > 5 个','8 分'],['EMPTY_IF','then 分支为空','8 分']];
 const tab=s.tables.add({rows:6,columns:3,left:40,top:114,width:880,height:302,columnWidths:[396,340,144],values});
 tab.borders.assign({fill:'#9EA7A8',width:1,style:'solid'});
 for(let r=0;r<6;r++){
   tab.rows[r].height=r===0?46:51.2;
   for(let c=0;c<3;c++){
     const cell=tab.getCell(r,c);cell.fill=r===0?C.blue:(r%2?C.white:C.sage);
     cell.text.style={fontSize:r===0?25:21,typeface:c===0&&r>0?F.code:F.body,color:C.ink,bold:r===0,alignment:c===2?'center':'left',verticalAlignment:'middle',insets:{left:14,right:10,top:8,bottom:6},autoFit:'none'};
   }
 }
 text(s,'评分 = max(0, 100 − 问题扣分总和)',110,445,800,45,28,{font:F.head,align:'center',fill:C.lav});
 text(s,'阈值是项目规则，未命中不代表业务正确',150,505,660,25,19,{align:'center'});
}
// 7. Simplified editable data relationship diagram.
{
 const s=slide(7,'数据设计：草稿、快照与问题谱系','扫描观察与持久身份分开，行号只负责定位');
 const data=[['projects','当前草稿',C.blue],['scan_runs','当次源码快照',C.sage],['scan_issues','本次问题观察\nissueKey',C.peach],['issue_lineages','问题生命周期\nlineageKey',C.lav],['reward_logs','奖励来源记录',C.blue]];
 const nodes=data.map(([h,t,c],i)=>{
   const x=30+i*186;const q=box(s,`${h}\n\n${t}`,x,185,160,190,c,h==='issue_lineages'?16.5:18,{font:F.body});return q;
 });
 for(let i=0;i<4;i++)conn(s,nodes[i],nodes[i+1]);
 text(s,'1—N',192,218,25,35,14,{font:F.code,align:'center'});text(s,'1—N',378,218,25,35,14,{font:F.code,align:'center'});
 text(s,'按 lineage_key 连接持久身份',402,146,335,28,16,{font:F.body,align:'center'});
 const growth=box(s,'users / user_monsters\n累计 XP、发现与收容统计',654,435,276,65,C.sage,18);
 conn(s,nodes[4],growth,'bottom','top');
 text(s,'issueKey = 本次观察\nlineageKey = 持久谱系',40,426,540,69,23,{font:F.code});
 text(s,'简化关系示意，非完整九表 ER 图；右侧为事务内业务更新',40,505,880,28,16,{color:C.muted});
}
// 8. Award idempotence timeline and transaction.
{
 const s=slide(8,'同一问题，只奖励一次','方法连续、问题消失，且谱系尚未领奖，才可确认首次有效收容');
 const vals=[['发现问题','生成 lineageKey'],['首次修复','+20 XP'],['再次扫描','+0 XP'],['复发再修','+0 XP']];
 const nodes=vals.map(([h,t],i)=>box(s,`${h}\n\n${t}`,40+i*230,155,190,135,[C.blue,C.sage,C.blue,C.lav][i],23));
 for(let i=0;i<3;i++)conn(s,nodes[i],nodes[i+1]);
 ['删除不算修复','歧义冻结','失败不换成功基线'].forEach((t,i)=>box(s,t,40+i*307,326,267,54,C.peach,21));
 frame(s,40,414,880,92,C.sage);text(s,'@Transactional + 项目行锁',57,423,450,27,21,{font:F.code});
 text(s,'写奖励记录  →  更新 XP  →  更新图鉴',160,459,640,35,24,{font:F.head,align:'center'});
 text(s,'UNIQUE(project_id, lineage_key)',230,507,560,28,17.5,{font:F.code,align:'center'});
}
// 9. Preserve the actual original screenshot as a replaceable image.
{
 const s=slide(9,'真实功能：编辑、定位、历史联动','基于 Java 后端的检测、修复与收容，形成完整操作闭环');
 await img(s,'orders-browser.png',30,142,650,365,null,'历史真实运行截图；原图直接嵌入，未经生成式重绘');
 const labels=[['Monaco 编辑与定位','编辑完整 Java 文件\n问题联动代码位置'],['历史快照与 AST','历史与当前草稿分开\nAST 对应当次扫描'],['图鉴、成长与日志','依据真实后端结果\n展示成长与事件']];
 labels.forEach(([h,t],i)=>{text(s,h,715,146+i*121,215,33,22,{font:F.head,fill:[C.blue,C.peach,C.sage][i]});text(s,t,716,192+i*121,214,60,18);});
 text(s,'历史运行截图；分数与 XP 以现场后端返回为准',40,510,880,26,15,{align:'center',color:C.muted});
}
// 10. Editable before/after source excerpts and evidence scores.
{
 const s=slide(10,'两个案例：小改动，明确反馈');
 line(s,480,116,0,383,'#A8AFAC',1);
 text(s,'订单导入',40,114,400,39,29,{font:F.head,fill:C.blue});text(s,'补上金额错误的行号与原因',40,167,400,36,21);
 const c1=box(s,'catch (NumberFormatException e) { }',40,221,410,61,C.peach,17,{font:F.code,align:'left'});
 const c2=box(s,'记录失败行号 + 金额原因',40,315,410,46,C.sage,22);conn(s,c1,c2,'bottom','top');
 text(s,'能明确指出哪一行金额格式错误，并给出原因',48,373,392,67,22);
 text(s,'85  →  100',105,454,300,41,34,{font:F.head,align:'center'});
 text(s,'VIP 优惠',520,114,400,39,29,{font:F.head,fill:C.blue});text(s,'删除 if 条件后的多余分号',520,167,400,36,21);
 const v1=box(s,'if (order.vip());',520,229,180,114,C.peach,17,{font:F.code});
 const v2=box(s,'if (order.vip())',740,229,180,114,C.sage,17,{font:F.code});conn(s,v1,v2);
 text(s,'折扣重新受条件控制，只有 VIP 订单才应用优惠',528,373,390,67,22);
 text(s,'92  →  100',585,454,300,41,34,{font:F.head,align:'center'});
 text(s,'源码片段为节选；分数为已验证样例结果，静态规则通过不替代业务测试',40,505,880,28,16,{align:'center',color:C.muted});
}
// 11. AI suggestion and independent original scanning path.
{
 const s=slide(11,'AI 扩展：建议仍需独立验证','当前草稿、修复预览、用户确认、原扫描验证分别承担不同职责');
 const v=[['当前草稿','用户正在编辑\n的完整源码'],['修复预览','DeepSeek 建议\n仅作预览'],['确认应用','校验上下文\n再替换草稿'],['AST 验证收容','重新扫描\n由原链路判定奖励']];
 const nodes=v.map(([h,t],i)=>box(s,`${h}\n\n${t}`,40+i*230,155,190,160,[C.blue,C.lav,C.peach,C.sage][i],21));
 for(let i=0;i<3;i++)conn(s,nodes[i],nodes[i+1]);
 text(s,'DeepSeekClient / AiRepairService',40,340,430,28,20,{font:F.code,align:'center'});
 text(s,'模型调用、输出约束与静态校验',40,376,430,27,20,{align:'center'});
 text(s,'ScanService 等原扫描与奖励链路',620,343,310,58,20,{font:F.code,align:'center'});
 frame(s,40,420,430,82,C.blue);text(s,'一次真实 API 调用\n返回源码的五规则问题：1 → 0',56,433,398,62,21);
 frame(s,502,420,418,82,C.peach);text(s,'AI 本身不保存扫描、不结算 XP\n浏览器点击验收待补充',517,433,388,62,20);
 text(s,'静态校验不证明业务正确',300,509,400,27,16,{align:'center',color:C.muted});
}
// 12. Course takeaways; all explanatory copy remains native text.
{
 const s=slide(12,'完成情况与 Java 课程收获');
 const rows=[['接口与多态','五条规则复用统一契约',C.blue],['集合、Stream 与 record','组织检测结果与数据传输',C.peach],['REST、MyBatis 与事务','把规则组成可追溯的业务闭环',C.lav]];
 rows.forEach(([h,t,c],i)=>{text(s,h,42,139+i*96,590,34,27,{font:F.head,fill:c});text(s,t,42,181+i*96,590,35,22);});
 await img(s,'slide_12.png',693,122,237,259,{left:.729,top:.366,right:.008,bottom:.225},'原版总结页的独立怪兽收容插画，可移动、缩放或替换');
 text(s,'谢谢，欢迎提问',690,416,240,48,30,{font:F.head,align:'center'});
 text(s,'已完成本机课程 MVP\n后续补业务回归、扩展规则，再评估多用户',42,439,600,62,22);
 text(s,'仅静态分析，不在线执行或编译用户源码',170,507,680,28,17,{align:'center',color:C.muted});
}

await fs.mkdir(path.join(D,'render'),{recursive:true});
for(const [i,s] of p.slides.items.entries()){
 const png=await p.export({slide:s,format:'png',scale:1.5});
 await fs.writeFile(path.join(D,'render',`slide_${String(i+1).padStart(2,'0')}.png`),new Uint8Array(await png.arrayBuffer()));
 counts.push({slide:i+1,shapes:s.shapes.items.length,images:s.images.items.length,tables:s.tables.items.length});
}
const candidate=path.join(D,'candidate.pptx');
await(await PresentationFile.exportPptx(p)).save(candidate);
await fs.writeFile(path.join(D,'editable-object-counts.json'),JSON.stringify(counts,null,2));
console.log(JSON.stringify({candidate,slides:p.slides.items.length,counts},null,2));
if(process.env.PPT_FINALIZE==='1'){
 const {finalizePresentation}=await import(pathToFileURL(path.join(SKILL,'container_tools/artifact_tool_utils.mjs')).href);
 // This sandbox permits copying but not hard links. Keep exclusive-output semantics.
 const originalLink=fs.link;
 fs.link=async(source,destination)=>fs.copyFile(source,destination,fsConstants.COPYFILE_EXCL);
 const result=await finalizePresentation({workspaceDir:ROOT,candidatePath:candidate,finalPath:OUT,pythonExecutable:PY,integrityValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_package_integrity.py'),layoutValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_layout_geometry.py'),explicitTotalSlideCount:12,requiredNativeTableOwnerSlides:[6],requiredNativeChartOwnerSlides:[],layoutArgs:['--expected-slide-size-emu','9144000,5143500','--validate-bullet-geometry','--validate-heading-fit','--require-native-table-slide','6'],fontPolicy:{basis:'design',families:[F.head,F.body,F.code]},verifyArtifactToolImport:true,receiptPath:path.join(D,'validation.json')});
 fs.link=originalLink;
 console.log(JSON.stringify(result));
}
