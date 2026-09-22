// 奇妙音乐屋 桌面客户端启动器（真 exe）
// 逻辑与 desktop\start.vbs 完全一致，只是编译成原生 exe、带图标、无控制台窗口：
//   1) 探测后端 http://127.0.0.1:8080/api；没起来就用同目录上层的 jar 起一个（隐藏）
//   2) 起本地静态服务（node server.cjs，提供 dist，127.0.0.1:5180）
//   3) 用 Edge 应用模式开窗（无地址栏/标签页）
// 编译：
//   csc /target:winexe /out:MusicHouse.exe /win32icon:..\dist\favicon.ico MusicHouse.cs
using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Threading;

class MusicHouseLauncher
{
    static readonly string BackendUrl = "http://127.0.0.1:8080/api/top/song?type=1";
    static readonly string AppUrl = "http://127.0.0.1:5180/";
    const int StaticPort = 5180;

    [STAThread]
    static void Main()
    {
        string baseDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd('\\');
        string projectDir = Directory.GetParent(baseDir).FullName;
        string distDir = Path.Combine(projectDir, "dist");
        string jarPath = FindJar(Path.Combine(projectDir, @"music-platform\music-server\target")) ?? "";
        string logFile = Path.Combine(baseDir, "launcher.log");

        Log(logFile, "=== launcher(exe) start ===");

        // 1) 后端
        bool backendUp = UrlOk(BackendUrl, 2000);
        Log(logFile, "backend up (before) = " + backendUp);
        if (!backendUp && File.Exists(jarPath))
        {
            Log(logFile, "starting backend jar: " + jarPath);
            RunHidden("java", "-jar \"" + jarPath + "\" >> \"" + Path.Combine(baseDir, "backend.log") + "\" 2>&1");
            for (int i = 0; i < 20 && !backendUp; i++)
            {
                Thread.Sleep(1500);
                backendUp = UrlOk(BackendUrl, 2000);
            }
        }
        Log(logFile, "backend up (after) = " + backendUp);

        // 2) 静态服务
        if (!UrlOk("http://127.0.0.1:" + StaticPort + "/", 1200))
        {
            Log(logFile, "starting static server on " + StaticPort);
            RunHidden("node", "\"" + Path.Combine(baseDir, "server.cjs") + "\" >> \"" + Path.Combine(baseDir, "server.log") + "\" 2>&1");
            for (int i = 0; i < 12; i++)
            {
                Thread.Sleep(700);
                if (UrlOk("http://127.0.0.1:" + StaticPort + "/", 1200)) break;
            }
        }
        Log(logFile, "static server up = " + UrlOk("http://127.0.0.1:" + StaticPort + "/", 1500));

        // 3) 应用窗口
        string edge = @"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe";
        if (!File.Exists(edge)) edge = @"C:\Program Files\Microsoft\Edge\Application\msedge.exe";
        if (File.Exists(edge))
        {
            string args = "--app=" + AppUrl
                + " --user-data-dir=\"" + Path.Combine(baseDir, "edge-profile") + "\""
                + " --window-size=1280,820 --no-first-run --no-default-browser-check";
            Log(logFile, "opening app window");
            Process.Start(new ProcessStartInfo(edge, args) { UseShellExecute = false });
        }
        else
        {
            Log(logFile, "Edge not found");
        }
        Log(logFile, "=== launcher(exe) done ===");
    }

    /// <summary>在给定目录及其上层查找 music-server\target 下的 jar（项目搬动后仍可用）</summary>
    static string FindJar(string preferredDir)
    {
        var candidates = new System.Collections.Generic.List<string>();
        candidates.Add(preferredDir);
        var dir = new DirectoryInfo(Path.GetDirectoryName(preferredDir.TrimEnd('\\')) ?? preferredDir);
        for (int i = 0; i < 6 && dir != null; i++)
        {
            candidates.Add(Path.Combine(dir.FullName, @"music-platform\music-server\target"));
            candidates.Add(Path.Combine(dir.FullName, @"target"));
            dir = dir.Parent;
        }
        foreach (var c in candidates)
        {
            try
            {
                if (!Directory.Exists(c)) continue;
                var jars = Directory.GetFiles(c, "music-server*.jar");
                if (jars.Length > 0) return jars[0];
            }
            catch { }
        }
        return null;
    }

    static bool UrlOk(string url, int timeoutMs)
    {
        try
        {
            var req = (HttpWebRequest)WebRequest.Create(url);
            req.Timeout = timeoutMs;
            req.Method = "GET";
            using (var resp = (HttpWebResponse)req.GetResponse())
            {
                return (int)resp.StatusCode == 200;
            }
        }
        catch
        {
            return false;
        }
    }

    static void RunHidden(string exe, string args)
    {
        try
        {
            Process.Start(new ProcessStartInfo("cmd.exe", "/c " + exe + " " + args)
            {
                UseShellExecute = false,
                CreateNoWindow = true,
                WindowStyle = ProcessWindowStyle.Hidden
            });
        }
        catch (Exception e)
        {
            Console.WriteLine(e.Message);
        }
    }

    static void Log(string file, string msg)
    {
        try
        {
            File.AppendAllText(file, DateTime.Now.ToString("yyyy/MM/dd HH:mm:ss") + "  " + msg + Environment.NewLine);
        }
        catch { }
    }
}
