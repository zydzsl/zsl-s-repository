package zsl.agent.funtions;
import cn.hutool.json.JSONObject;
import org.springframework.stereotype.Component;
import zsl.agent.config.AiToolMethod;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class WYcloude {

    // 网易云音乐Windows默认安装路径
    private static final List<String> CLOUD_MUSIC_PATHS = Arrays.asList(
            "C:\\Program Files (x86)\\Netease\\CloudMusic\\cloudmusic.exe"
    );

    // ==================== 原有功能：启动/关闭网易云 ====================
    @AiToolMethod(name = "start_cloud_music", desc = "启动本地电脑上的网易云音乐客户端，可选自定义安装路径")
    public String startCloudMusic(JSONObject params) {
        String customPath = params.getStr("customPath");
        if (customPath != null && !customPath.isBlank()) {
            return startProcess(customPath);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "cloudmusic.exe");
            pb.start();
            return "✅ 网易云音乐启动成功！";
        } catch (IOException ignored) {}

        for (String path : CLOUD_MUSIC_PATHS) {
            File exeFile = new File(path);
            if (exeFile.exists() && exeFile.isFile()) {
                return startProcess(path);
            }
        }

        return "❌ 未找到网易云音乐安装路径，请确认已安装客户端，或传入自定义exe路径重试。";
    }

    @AiToolMethod(name = "stop_cloud_music", desc = "关闭本地电脑上的网易云音乐客户端")
    public String stopCloudMusic(JSONObject params) {
        try {
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/f", "/im", "cloudmusic.exe");
            Process process = pb.start();
            process.waitFor(3, TimeUnit.SECONDS);
            return "✅ 网易云音乐已关闭！";
        } catch (Exception e) {
            return "❌ 关闭失败：" + e.getMessage();
        }
    }

    // ==================== 【核心修复】URI 协议版：启动并自动播放 ====================
    @AiToolMethod(name = "start_and_play_music", desc = "启动网易云音乐并自动播放，无需参数，完美支持无界面环境")
    public String startAndPlayMusic(JSONObject params) {
        // 1. 先启动客户端
        String startResult = startCloudMusic(params);
        if (startResult.contains("❌")) {
            return startResult;
        }

        // 2. 等待客户端启动完成
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 3. 【核心】用 URI 协议触发播放（不需要 Robot，不需要 GUI）
        return playPause(new JSONObject());
    }

    // ==================== 【核心修复】URI 协议版：播放/暂停 ====================
    @AiToolMethod(name = "music_play_pause", desc = "网易云音乐播放/暂停，无需图形界面，完美支持headless环境")
    public String playPause(JSONObject params) {
        try {
            // 【核心】orpheus://play 协议直接触发播放/暂停，不需要任何按键
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "orpheus://play");
            pb.start();
            return "✅ 已执行播放/暂停操作（URI协议，无需图形界面）";
        } catch (Exception e) {
            return "❌ 操作失败：" + e.getMessage();
        }
    }

    // ==================== 【核心修复】URI 协议版：下一首 ====================
    @AiToolMethod(name = "music_next", desc = "切换到网易云音乐下一首歌曲，无需图形界面")
    public String nextSong(JSONObject params) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "orpheus://next");
            pb.start();
            return "✅ 已切换到下一首";
        } catch (Exception e) {
            return "❌ 操作失败：" + e.getMessage();
        }
    }

    // ==================== 【核心修复】URI 协议版：上一首 ====================
    @AiToolMethod(name = "music_prev", desc = "切换到网易云音乐上一首歌曲，无需图形界面")
    public String prevSong(JSONObject params) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "orpheus://prev");
            pb.start();
            return "✅ 已切换到上一首";
        } catch (Exception e) {
            return "❌ 操作失败：" + e.getMessage();
        }
    }

    // ==================== 【核心修复】URI 协议版：搜索音乐 ====================
    @AiToolMethod(name = "music_search", desc = "打开网易云音乐并搜索指定关键词的音乐，无需图形界面")
    public String searchMusic(JSONObject params) {
        String keyword = params.getStr("keyword");
        if (keyword == null || keyword.isBlank()) {
            return "❌ 请传入搜索关键词";
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "orpheus://search/" + keyword);
            pb.start();
            return "✅ 已打开网易云音乐，搜索关键词：" + keyword;
        } catch (Exception e) {
            return "❌ 搜索失败：" + e.getMessage();
        }
    }

    // ==================== 【核心修复】URI 协议版：播放指定歌曲 ====================
    @AiToolMethod(name = "music_play_song", desc = "打开网易云音乐并播放指定ID的歌曲，无需图形界面")
    public String playSongById(JSONObject params) {
        String songId = params.getStr("songId");
        if (songId == null || songId.isBlank()) {
            return "❌ 请传入歌曲ID";
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "orpheus://song/" + songId);
            pb.start();
            return "✅ 已打开网易云音乐，播放歌曲ID：" + songId;
        } catch (Exception e) {
            return "❌ 播放失败：" + e.getMessage();
        }
    }

    // ==================== 【核心修复】URI 协议版：播放指定歌单 ====================
    @AiToolMethod(name = "music_play_playlist", desc = "打开网易云音乐并播放指定ID的歌单，无需图形界面")
    public String playPlaylistById(JSONObject params) {
        String playlistId = params.getStr("playlistId");
        if (playlistId == null || playlistId.isBlank()) {
            return "❌ 请传入歌单ID";
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "orpheus://playlist/" + playlistId);
            pb.start();
            return "✅ 已打开网易云音乐，播放歌单ID：" + playlistId;
        } catch (Exception e) {
            return "❌ 播放失败：" + e.getMessage();
        }
    }

    // ==================== 通用工具方法 ====================
    private String startProcess(String exePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(exePath);
            pb.start();
            return "✅ 网易云音乐启动成功！路径：" + exePath;
        } catch (Exception e) {
            return "❌ 启动失败：" + e.getMessage();
        }
    }
}