---
name: ncm_cli_play_workflow
description: Correct workflow for playing a song with ncm-cli
type: project
---
ncm-cli 播放歌曲的正确流程：
1. 搜索歌曲：ncm-cli search song --keyword "歌名 歌手" --limit 1 --output human（务必加 --limit 限制返回数量，否则结果太大会被 compact）
2. 从结果中提取 encrypted-id（32位hex）和 original-id（数字）
3. 播放：ncm-cli play --song --encrypted-id <加密ID> --original-id <原始ID> --output human
4. 查看状态：ncm-cli state --output human

关键教训：search song 用 --keyword 选项而非直接传参；必须加 --limit 防止输出过大被截断。
