package zsl.agent.utils;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import zsl.agent.entry.OpenAiTool;
import zsl.agent.funtions.McpBootstrap;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


import static zsl.agent.funtions.McpBootstrap.allTools;


/**
 * 工具常量配置（对应Python的常量、TOOLS定义）
 */
public class ToolConstants{
    // 工作目录 WORKDIR = Path.cwd()
    public static final Path WORKDIR = Paths.get(System.getProperty("user.dir"));

    public static final MCPToolRouter mcpRouter = new MCPToolRouter();

    // 危险命令黑名单
    public static final Set<String> DANGEROUS_COMMANDS = new HashSet<>();
    static {
        DANGEROUS_COMMANDS.add("rm -rf /");
        DANGEROUS_COMMANDS.add("sudo");
        DANGEROUS_COMMANDS.add("shutdown");
        DANGEROUS_COMMANDS.add("reboot");
        DANGEROUS_COMMANDS.add("> /dev/");
    }

    // 并发安全/不安全工具
    public static final Set<String> CONCURRENCY_SAFE = new HashSet<>();
    public static final Set<String> CONCURRENCY_UNSAFE = new HashSet<>();
    static {
        CONCURRENCY_SAFE.add("read_file");
        CONCURRENCY_UNSAFE.add("write_file");
        CONCURRENCY_UNSAFE.add("edit_file");
        CONCURRENCY_UNSAFE.add("bash");
    }

    // ===================== AI工具定义 TOOLS =====================
    public static final JSONArray TOOLS;
    public static final JSONArray SubTOOLS;


    static {
        JSONArray tools1;
        String toolsJson = """
    [
        {
            "type": "function",
            "function": {
                "name": "bash",
                "description": "Run a shell command.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "command": {"type": "string"}
                    },
                    "required": ["command"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "read_file",
                "description": "Read file contents.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {"type": "string"},
                        "limit": {"type": "integer"}
                    },
                    "required": ["path"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "write_file",
                "description": "Write content to file.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {"type": "string"},
                        "content": {"type": "string"}
                    },
                    "required": ["path", "content"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "edit_file",
                "description": "Replace exact text in file.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {"type": "string"},
                        "old_text": {"type": "string"},
                        "new_text": {"type": "string"}
                    },
                    "required": ["path", "old_text", "new_text"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "todo",
                "description": "Rewrite the current session plan for multi-step work.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "items": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "content": {"type": "string"},
                                    "status": {"type": "string","enum": ["pending", "in_progress", "completed"]},
                                    "active_form": {"type": "string","description": "Optional present-continuous label."}
                                },
                                "required": ["content", "status","active_form"]
                            }
                        }
                    },
                    "required": ["items"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "subagent",
                "description": "Spawn a subagent with fresh context. It shares the filesystem but not conversation history.",
                "parameters":{
                    "type": "object",
                    "properties": {
                        "prompt": {"type": "string"},
                        "desc": {"type": "string", "description": "Short description of the task"}
                    },
                    "required": ["prompt","desc"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "load_skill",
                "description": "Load a skill.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"}
                    },
                    "required": ["name"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "compact",
                "description": "Summarize earlier conversation so work can continue in a smaller context.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "focus": {"type": "string"}
                    },
                    "required": ["focus"]
                }
            }
        },
        {
            "type": "function",
            "function": {
              "name": "save_memory",
              "description": "Save a persistent memory that survives across sessions.",
              "parameters": {
                "type": "object",
                "properties": {
                  "name": {"type": "string","description": "Short identifier"},
                  "memo": {"type": "string","description": "One-line summary"},
                  "type": {"type": "string","enum": ["user", "feedback", "project", "reference"]},
                  "content": {"type": "string"}
                },
                "required": ["name", "memo", "type", "content"]
              }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "task_create",
                "description": "Create a new task.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "subject": {"type": "string"},
                        "description": {"type": "string"}
                    },
                    "required": ["subject","description"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "task_update",
                "description": "Update a task's status, owner, or dependencies.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "task_id": {"type": "integer"},
                        "status": {"type": "string","enum": ["pending","in_progress","completed","deleted"]},
                        "owner": {"type": "string"},
                        "addBlockedBy": {"type": "array","items": {"type": "integer"}},
                        "addBlocks": {"type": "array","items": {"type": "integer"}}
                    },
                    "required": ["task_id","status"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "task_list",
                "description": "List all tasks with status summary.",
                "parameters": {
                    "type": "object",
                    "properties": {}
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "task_get",
                "description": "Get full details of a task by ID.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "task_id": {"type": "integer"}
                    },
                    "required": ["task_id"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "bg_run",
                "description": "Run a shell command in background (async, non-blocking).",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "command": {"type": "string"}
                    },
                    "required": ["command"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "bg_check",
                "description": "Check status of a background task or list all tasks.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "task_id": {"type": "string", "description": "Optional task ID"}
                    },
                    "required": ["task_id"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "bg_drain",
                "description": "Get and clear all completed task notifications.",
                "parameters": {
                    "type": "object",
                    "properties": {}
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "cron_create",
                "description": "Schedule a recurring or one-shot task with a cron expression.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "cron": {"type": "string"},
                        "prompt": {"type": "string"},
                        "recurring": {"type": "boolean"},
                        "durable": {"type": "boolean"}
                    },
                    "required": ["cron", "prompt","recurring","durable"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "cron_delete",
                "description": "Delete a scheduled task by ID.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "id": {"type": "string"}
                    },
                    "required": ["id"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "cron_list",
                "description": "List all scheduled tasks.",
                "parameters": {
                    "type": "object",
                    "properties": {}
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "send_message",
                "description": "Send message to a teammate.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "sender": {"type": "string"},
                        "to": {"type": "string"},
                        "content": {"type": "string"},
                        "msg_type": {"type": "string","enum": ["message","broadcast","shutdown_request","shutdown_response","plan_approval","plan_approval_response"]}
                    },
                    "required": ["sender","to","content","msg_type"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "read_inbox",
                "description": "Read and drain your inbox.",
                "parameters": {
                    "type": "object",
                    "properties": {
                    "name": {"type": "string"},
                    },
                    "required": [name]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "broadcast",
                "description": "Send a message to all teammates.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "content": {"type": "string"},
                        "sender": {"type": "string"}
                    },
                    "required": ["content","sender"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "spawn_teammate",
                "description": "Spawn a persistent teammate that runs in its own thread.",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "role": {"type": "string"},
                        "prompt": {"type": "string"}
                    },
                    "required": ["name", "role", "prompt"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "list_teammates",
                "description": "List all teammates with name, role, status.",
                "parameters": {
                    "type": "object",
                    "properties": {},
                    "required": []
                }
            }
        }
    ]
    """;
        tools1 = JSONUtil.parseArray(toolsJson);
        List<OpenAiTool> nativeTools =JSONUtil.toList(tools1, OpenAiTool.class);
        PluginLoader pluginLoader = new PluginLoader();
        McpBootstrap.init(pluginLoader, mcpRouter);


        // 2. 获取 MCP 工具
//        List<OpenAiTool> mcpTools = allTools;
        List<OpenAiTool> mcpTools = mcpRouter.getAllTools();

        // 3. 去重（原生优先）
        List<OpenAiTool> allTools = new ArrayList<>(nativeTools);
        Set<String> exists = new HashSet<>();
        for (OpenAiTool t : nativeTools) exists.add(t.function.name);
        for (OpenAiTool t : mcpTools) {
            if (!exists.contains(t.function.name)) {
                allTools.add(t);
            }
        }
        // 4. 转成 JSON 字符串返回（接口用）
        tools1 = JSONUtil.parseArray(allTools);
        TOOLS = tools1;
    }
    static {
        String toolsJson = """
            [
                {
                    "type": "function",
                    "function": {
                        "name": "bash",
                        "description": "Run a shell command.",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "command": {"type": "string"}
                            },
                            "required": ["command"]
                        }
                    }
                },
                {
                    "type": "function",
                    "function": {
                        "name": "read_file",
                        "description": "Read file contents.",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "path": {"type": "string"},
                                "limit": {"type": "integer"}
                            },
                            "required": ["path"]
                        }
                    }
                },
                {
                    "type": "function",
                    "function": {
                        "name": "write_file",
                        "description": "Write content to file.",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "path": {"type": "string"},
                                "content": {"type": "string"}
                            },
                            "required": ["path", "content"]
                        }
                    }
                },
                {
                    "type": "function",
                    "function": {
                        "name": "edit_file",
                        "description": "Replace exact text in file.",
                        "parameters": {
                            "type": "object",
                            "properties": {
                                "path": {"type": "string"},
                                "old_text": {"type": "string"},
                                "new_text": {"type": "string"}
                            },
                            "required": ["path", "old_text", "new_text"]
                        }
                    }
                },
                  {
                            "type": "function",
                            "function": {
                                "name": "todo",
                                "description": "Rewrite the current session plan for multi-step work.",
                                "parameters": {
                                    "type": "object",
                                    "properties": {
                                        "items": {
                                            "type": "array",
                                            "items": {
                                                "type": "object",
                                                "properties": {
                                                    "content": {
                                                        "type": "string"
                                                    },
                                                    "status": {
                                                        "type": "string",
                                                        "enum": ["pending", "in_progress", "completed"]
                                                    },
                                                    "active_form": {
                                                        "type": "string",
                                                        "description": "Optional present-continuous label."
                                                    }
                                                },
                                                "required": ["content", "status", "active_form"]
                                            }
                                        }
                                    },
                                    "required": ["items"]
                                }
                            }
                        },
                        {
                                    "type": "function",
                                    "function": {
                                        "name": "load_skill",
                                        "description": "Load a skill.",
                                        "parameters": {
                                            "type": "object",
                                            "properties": {
                                                "name": {"type": "string"}
                                            },
                                            "required": ["name"]
                                        }
                                    }
                        },
                        {
                                "type": "function",
                                "function": {
                                  "name": "save_memory",
                                  "description": "Save a persistent memory that survives across sessions.",
                                  "parameters": {
                                    "type": "object",
                                    "properties": {
                                      "name": {
                                        "type": "string",
                                        "description": "Short identifier (e.g. prefer_tabs, db_schema)"
                                      },
                                      "description": {
                                        "type": "string",
                                        "description": "One-line summary of what this memory captures"
                                      },
                                      "type": {
                                        "type": "string",
                                        "enum": ["user", "feedback", "project", "reference"],
                                        "description": "user=preferences, feedback=corrections, project=non-obvious project conventions or decision reasons, reference=external resource pointers"
                                      },
                                      "content": {
                                        "type": "string",
                                        "description": "Full memory content (multi-line OK)"
                                      }
                                    },
                                    "required": ["name", "description", "type", "content"]
                                }
                            }
                        },
                         {
                "type": "function",
                "function": {
                    "name": "send_message",
                    "description": "Send message to a teammate.",
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "sender": {
                                "type": "string",
                                "description": "your name."
                            },
                            "to": {
                                "type": "string",
                                "description": "The name of the teammate to receive the message"
                            },
                            "content": {
                                "type": "string",
                                "description": "The content of the message to send"
                            },
                            "msg_type": {
                                "type": "string",
                                "description": "The type of the message",
                                "enum": [
                                    "message",
                                    "broadcast",
                                    "shutdown_request",
                                    "shutdown_response",
                                    "plan_approval",
                                    "plan_approval_response"
                                ]
                            }
                        },
                        "required": [
                            "sender",
                            "to",
                            "content"
                        ]
                    }
                }
            },
            {
                "type": "function",
                "function": {
                    "name": "read_inbox",
                    "description": "Read and drain your inbox.",
                    "parameters": {
                        "type": "object",
                        "properties": {},
                        "required": []
                    }
                }
            }
            ]
            """;
        // 解析字符串为 JSONArray
        SubTOOLS = JSONUtil.parseArray(toolsJson);
    }
}
