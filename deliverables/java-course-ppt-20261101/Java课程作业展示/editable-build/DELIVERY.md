# PPT-JAVA02-EDIT

2026-11-01，责任人：总控。用户授权将已指定的12页图片式课件转换为可二次编辑的PPTX。

交付：../editable-output/Java课程作业展示_可编辑版.pptx。12页、原10×5.625英寸尺寸、213个可编辑文本片段、1张原生规则表格、原生形状/连接线技术图示、全部12页讲稿备注。3张独立图片分别为封面插画、原始历史运行截图、结尾插画；可移动/缩放/替换，图片内部细节不逐项拆分。

原内容与页序保留，原生线条和字体重建边框与排版，外观与图片版有少量差异。第5页示意代码改用简短Exception以保持完整行展示；真实业务案例保留NumberFormatException。截图从用户既有项目历史归档中读取原始PNG，不修改归档或业务文件。原图片PPT不改写。

工具：@oai/artifact-tool，打包Node/Python运行时。脚本build_editable.mjs；临时构建、渲染和验证留在本目录。未使用python-pptx作者接口，未改全局技能或运行时依赖。Windows沙箱不支持创建硬链接，最终发布步骤采用COPYFILE_EXCL复制经验证的原始字节，保留不覆盖语义。

关键检查：全部页已渲染并逐页目视检查；修复类名换行、代码断行、页脚位置；包完整性、尺寸/布局/字体策略、原生表格和第一方重新导入通过。另用ZIP/XML检查每页均有原生文字、全部讲稿备注非空，规则表位于第6页。详见validation.json、verification.json。未在原生PowerPoint/WPS打开检查。

构建命令：设PPT_FINALIZE=1及RUNTIME_NODE_MODULES为Codex打包依赖目录后，用打包Node运行build_editable.mjs。最终器不覆盖既有输出，再次构建需使用新的输出文件名。

无业务扫描、XP或数据库变更。下一步由用户在PowerPoint/WPS中二次编辑。
