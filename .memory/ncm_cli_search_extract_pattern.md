---
name: ncm_cli_search_extract_pattern
description: ncm-cli 搜索后用 node 管道提取关键字段防止 compact
type: project
---
ncm-cli 搜索歌曲后通过管道用 node 脚本提取关键字段，避免 JSON 输出过大被 compact。命令模板：
ncm-cli search song --keyword "关键词" --limit 5 --output json 2>&1 | node -e "var s='';process.stdin.on('data',d=>s+=d);process.stdin.on('end',()=>{var j=JSON.parse(s);j.data.records.forEach((r,i)=>console.log((i+1)+'. '+r.name+' - '+r.fullArtists.map(a=>a.name).join('/')+' | 加密ID:'+r.id+' | 原始ID:'+r.originalId))})"

输出格式：序号. 歌名 - 歌手 | 加密ID:xxx | 原始ID:xxx
