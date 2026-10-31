package com.example.music.domain;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import com.example.music.domain.server.Cpu;
import com.example.music.domain.server.Jvm;
import com.example.music.domain.server.Mem;
import com.example.music.domain.server.Sys;
import com.example.music.domain.server.SysFile;
import lombok.Data;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.CentralProcessor.TickType;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.PowerSource;
import oshi.hardware.Sensors;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import oshi.util.Util;

/**
 * 服务器运行时信息（移植自若依/zzyl 监控模块并大幅扩充，基于 OSHI）
 * <p>
 * 覆盖：CPU / 物理内存+内存条 / 虚拟内存 / JVM / 系统 / 磁盘容量 / 磁盘 IO 速率 /
 * 网络接口收发 / 传感器（温度·电压·风扇） / 电源（电池）
 */
@Data
public class Server {

    private static final int OSHI_WAIT_SECOND = 1000;

    /** CPU 信息 */
    private Cpu cpu = new Cpu();
    /** 内存信息 */
    private Mem mem = new Mem();
    /** JVM 信息 */
    private Jvm jvm = new Jvm();
    /** 服务器信息 */
    private Sys sys = new Sys();
    /** 磁盘容量信息 */
    private List<SysFile> sysFiles = new LinkedList<>();
    /** 内存条（物理内存插槽）明细 */
    private List<Map<String, Object>> memBanks = new LinkedList<>();
    /** 磁盘 IO 速率（1 秒采样） */
    private List<Map<String, Object>> diskIos = new LinkedList<>();
    /** 网络接口收发（1 秒采样） */
    private List<Map<String, Object>> netIfs = new LinkedList<>();
    /** GC 情况 */
    private List<Map<String, Object>> gcInfo = new LinkedList<>();
    /** 传感器：CPU 温度 / 电压 / 风扇 */
    private Map<String, Object> sensors = new LinkedHashMap<>();
    /** 电源（台式机为空） */
    private List<Map<String, Object>> power = new LinkedList<>();

    /**
     * 采集全部信息（CPU 与磁盘 IO 采样会阻塞约 1 秒，与若依实现一致）
     */
    public void copyTo() throws Exception {
        SystemInfo si = new SystemInfo();
        HardwareAbstractionLayer hal = si.getHardware();

        CentralProcessor processor = hal.getProcessor();
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        List<HWDiskStore> prevDisks = snapshotDisks(hal);
        List<NetworkIF> prevNics = snapshotNics(hal);

        Util.sleep(OSHI_WAIT_SECOND);

        setCpuInfo(processor, prevTicks, hal);
        setMemInfo(hal.getMemory());
        setSysInfo(si.getOperatingSystem());
        setJvmInfo();
        setSysFiles(si.getOperatingSystem());
        setDiskIos(hal, prevDisks);
        setNetIfs(hal, prevNics);
        setSensorsAndPower(hal);
    }

    private void setCpuInfo(CentralProcessor processor, long[] prevTicks, HardwareAbstractionLayer hal) {
        long[] ticks = processor.getSystemCpuLoadTicks();
        long nice = ticks[TickType.NICE.getIndex()] - prevTicks[TickType.NICE.getIndex()];
        long irq = ticks[TickType.IRQ.getIndex()] - prevTicks[TickType.IRQ.getIndex()];
        long softirq = ticks[TickType.SOFTIRQ.getIndex()] - prevTicks[TickType.SOFTIRQ.getIndex()];
        long steal = ticks[TickType.STEAL.getIndex()] - prevTicks[TickType.STEAL.getIndex()];
        long cSys = ticks[TickType.SYSTEM.getIndex()] - prevTicks[TickType.SYSTEM.getIndex()];
        long user = ticks[TickType.USER.getIndex()] - prevTicks[TickType.USER.getIndex()];
        long iowait = ticks[TickType.IOWAIT.getIndex()] - prevTicks[TickType.IOWAIT.getIndex()];
        long idle = ticks[TickType.IDLE.getIndex()] - prevTicks[TickType.IDLE.getIndex()];
        long totalCpu = user + nice + cSys + idle + iowait + irq + softirq + steal;
        cpu.setCpuNum(processor.getLogicalProcessorCount());
        cpu.setPhysicalNum(processor.getPhysicalPackageCount() * processor.getPhysicalProcessorCount() > 0
                ? processor.getPhysicalProcessorCount() : processor.getLogicalProcessorCount());
        cpu.setCpuName(processor.getProcessorIdentifier().getName().trim());
        cpu.setTotal(totalCpu);
        cpu.setSys(cSys);
        cpu.setUsed(user);
        cpu.setWait(iowait);
        cpu.setFree(idle);
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactoryBeanHolder.OS;
            cpu.setProcessLoad(Math.round(osBean.getProcessCpuLoad() * 10000) / 100.0);
        } catch (Exception ignored) {
        }
    }

    private void setMemInfo(GlobalMemory memory) {
        mem.setTotal(memory.getTotal());
        mem.setUsed(memory.getTotal() - memory.getAvailable());
        mem.setFree(memory.getAvailable());
        // 虚拟内存（含页面文件）
        oshi.hardware.VirtualMemory vm = memory.getVirtualMemory();
        mem.setSwapTotal(vm.getVirtualMax());
        mem.setSwapUsed(vm.getVirtualInUse());
        // 内存条明细
        for (oshi.hardware.PhysicalMemory bank : memory.getPhysicalMemory()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bank", bank.getBankLabel() == null || bank.getBankLabel().isEmpty() ? "未知插槽" : bank.getBankLabel().trim());
            m.put("capacityGB", Math.round(bank.getCapacity() / 1073741824.0 * 10) / 10.0);
            m.put("type", (bank.getMemoryType() == null || bank.getMemoryType().isEmpty()
                    ? bank.getManufacturer() : bank.getMemoryType()).trim());
            m.put("speed", bank.getClockSpeed() > 1000000 ? bank.getClockSpeed() / 1000000 + " MT/s" : bank.getClockSpeed() + " MT/s");
            memBanks.add(m);
        }
    }

    private void setSysInfo(OperatingSystem os) throws Exception {
        Properties props = System.getProperties();
        InetAddress addr = InetAddress.getLocalHost();
        sys.setComputerName(addr.getHostName());
        sys.setComputerIp(addr.getHostAddress());
        sys.setOsName(props.getProperty("os.name"));
        sys.setOsArch(props.getProperty("os.arch"));
        sys.setUserDir(props.getProperty("user.dir"));
        sys.setOsVersion(os.getVersionInfo().getVersion());
        sys.setProcessCount(os.getProcessCount());
        sys.setThreadCount(os.getThreadCount());
        long boot = os.getSystemBootTime();
        long upSec = System.currentTimeMillis() / 1000 - boot;
        sys.setBootTime(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochSecond(boot)));
        sys.setOsUptime(upSec / 86400 + "天" + upSec % 86400 / 3600 + "小时" + upSec % 3600 / 60 + "分钟");
    }

    private void setJvmInfo() {
        Properties props = System.getProperties();
        jvm.setTotal(Runtime.getRuntime().totalMemory());
        jvm.setMax(Runtime.getRuntime().maxMemory());
        jvm.setFree(Runtime.getRuntime().freeMemory());
        jvm.setVersion(props.getProperty("java.version"));
        jvm.setHome(props.getProperty("java.home"));
        // GC 情况
        for (java.lang.management.GarbageCollectorMXBean gc : ManagementFactoryBeanHolder.GC_BEANS) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", gc.getName());
            m.put("count", gc.getCollectionCount());
            m.put("timeMs", gc.getCollectionTime());
            gcInfo.add(m);
        }
    }

    private void setSysFiles(OperatingSystem os) {
        FileSystem fileSystem = os.getFileSystem();
        for (OSFileStore fs : fileSystem.getFileStores()) {
            long free = fs.getUsableSpace();
            long total = fs.getTotalSpace();
            if (total <= 0) {
                continue; // 无介质的光驱/虚拟盘跳过
            }
            long used = total - free;
            SysFile sysFile = new SysFile();
            sysFile.setDirName(fs.getMount());
            sysFile.setSysTypeName(fs.getType());
            sysFile.setTypeName(fs.getName());
            sysFile.setTotal(convertFileSize(total));
            sysFile.setFree(convertFileSize(free));
            sysFile.setUsed(convertFileSize(used));
            sysFile.setUsage(Math.round((double) used / total * 10000) / 100.0);
            sysFiles.add(sysFile);
        }
    }

    /** 磁盘 IO：1 秒窗口内读/写字节数 → KB/s */
    private void setDiskIos(HardwareAbstractionLayer hal, List<HWDiskStore> prevDisks) {
        for (HWDiskStore cur : prevDisks) {
            cur.updateAttributes();
            HWDiskStore prev = prevDisks.stream()
                    .filter(d -> d.getName().equals(cur.getName())).findFirst().orElse(null);
            if (prev == null) continue;
            long dt = Math.max(1, cur.getTimeStamp() - prev.getTimeStamp());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", cur.getName());
            m.put("model", cur.getModel() == null ? "-" : cur.getModel().trim());
            m.put("sizeGB", Math.round(cur.getSize() / 1073741824.0));
            m.put("readKBs", Math.max(0, (cur.getReadBytes() - prev.getReadBytes()) / 1024 / dt));
            m.put("writeKBs", Math.max(0, (cur.getWriteBytes() - prev.getWriteBytes()) / 1024 / dt));
            m.put("totalReadGB", Math.round(cur.getReadBytes() / 1073741824.0 * 10) / 10.0);
            m.put("totalWriteGB", Math.round(cur.getWriteBytes() / 1073741824.0 * 10) / 10.0);
            diskIos.add(m);
        }
    }

    /** 网络接口：1 秒窗口内收/发字节数 → KB/s */
    private void setNetIfs(HardwareAbstractionLayer hal, List<NetworkIF> prevNics) {
        for (NetworkIF cur : prevNics) {
            cur.updateAttributes();
            NetworkIF prev = prevNics.stream()
                    .filter(n -> n.getName().equals(cur.getName())).findFirst().orElse(null);
            if (prev == null) continue;
            long dt = Math.max(1, cur.getTimeStamp() - prev.getTimeStamp());
            if (cur.getSpeed() <= 0 && cur.getBytesRecv() == 0 && cur.getBytesSent() == 0) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", cur.getName());
            m.put("displayName", cur.getDisplayName() == null ? "-" : cur.getDisplayName());
            m.put("ip", cur.getIPv4addr() == null || cur.getIPv4addr().length == 0 ? "-" : String.join(",", cur.getIPv4addr()));
            m.put("mac", cur.getMacaddr());
            m.put("speedMbps", cur.getSpeed() / 1000000);
            m.put("recvKBs", Math.max(0, (cur.getBytesRecv() - prev.getBytesRecv()) / 1024 / dt));
            m.put("sendKBs", Math.max(0, (cur.getBytesSent() - prev.getBytesSent()) / 1024 / dt));
            m.put("totalRecvGB", Math.round(cur.getBytesRecv() / 1073741824.0 * 10) / 10.0);
            m.put("totalSentGB", Math.round(cur.getBytesSent() / 1073741824.0 * 10) / 10.0);
            netIfs.add(m);
        }
    }

    private void setSensorsAndPower(HardwareAbstractionLayer hal) {
        Sensors s = hal.getSensors();
        sensors.put("cpuTemp", s.getCpuTemperature() > 0 ? s.getCpuTemperature() + " ℃" : "不支持");
        sensors.put("cpuVoltage", s.getCpuVoltage() > 0 ? s.getCpuVoltage() + " V" : "不支持");
        int[] fans = s.getFanSpeeds();
        sensors.put("fans", fans.length == 0 ? "不支持" : java.util.Arrays.toString(fans) + " RPM");
        for (PowerSource ps : hal.getPowerSources()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", ps.getName());
            m.put("remaining", Math.round(ps.getRemainingCapacityPercent() * 100));
            power.add(m);
        }
    }

    private List<HWDiskStore> snapshotDisks(HardwareAbstractionLayer hal) {
        List<HWDiskStore> list = hal.getDiskStores();
        for (HWDiskStore d : list) {
            d.updateAttributes();
        }
        return list;
    }

    private List<NetworkIF> snapshotNics(HardwareAbstractionLayer hal) {
        List<NetworkIF> list = hal.getNetworkIFs(true);
        for (NetworkIF n : list) {
            n.updateAttributes();
        }
        return list;
    }

    /** 字节转可读字符串 */
    public String convertFileSize(long size) {
        long kb = 1024;
        long mb = kb * 1024;
        long gb = mb * 1024;
        if (size >= gb) {
            return String.format("%.1f GB", (float) size / gb);
        } else if (size >= mb) {
            float f = (float) size / mb;
            return String.format(f > 100 ? "%.0f MB" : "%.1f MB", f);
        } else if (size >= kb) {
            float f = (float) size / kb;
            return String.format(f > 100 ? "%.0f KB" : "%.1f KB", f);
        } else {
            return String.format("%d B", size);
        }
    }

    /** 内部持有者：避免重复创建 MBean */
    private static class ManagementFactoryBeanHolder {
        private static final com.sun.management.OperatingSystemMXBean OS =
                (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        private static final List<java.lang.management.GarbageCollectorMXBean> GC_BEANS =
                java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();
    }
}
