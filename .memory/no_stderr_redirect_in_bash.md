---
name: no_stderr_redirect_in_bash
description: 执行 bash 命令不要加 2>&1 -- 这是高频违规项，每次都必须检查
type: feedback
---
用户反复强调：执行 bash 命令时绝对不要加 2>&1 重定向。这是高频遗忘项，在每次执行 bash 命令前必须检查命令中是否包含 2>&1，如有则去掉。直接在命令末尾不加任何 stderr 重定向即可。
