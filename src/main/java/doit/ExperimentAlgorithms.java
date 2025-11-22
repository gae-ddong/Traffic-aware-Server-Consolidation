package doit;

import java.util.List;
import java.util.Map;

public class ExperimentAlgorithms {

    private final TrafficSimulationCore core = new TrafficSimulationCore();

    public void run() {
        int hostCount = 20;
        int vmCount = 60;
        double supernodePercentile = 0.95;
        TrafficSimulationCore.Topology topo = TrafficSimulationCore.Topology.TREE;

        List<TrafficSimulationCore.HostInfo> hostsBase = core.createHostList(hostCount);
        List<TrafficSimulationCore.VmInfo> vmsBase = core.createVmList(vmCount);
        double[][] traffic = core.createTrafficMatrix(vmCount);

        Map<TrafficSimulationCore.VmInfo, TrafficSimulationCore.HostInfo> ffd =
                core.placeVmsFFD(core.copyHosts(hostsBase), core.copyVms(vmsBase));
        Map<TrafficSimulationCore.VmInfo, TrafficSimulationCore.HostInfo> sercon =
                core.placeVmsSercon(core.copyHosts(hostsBase), core.copyVms(vmsBase));
        Map<TrafficSimulationCore.VmInfo, TrafficSimulationCore.HostInfo> proposed =
                core.placeVmsProposed(core.copyHosts(hostsBase), core.copyVms(vmsBase),
                        traffic, supernodePercentile, topo);

        double tFfd = core.calcTrafficCost(ffd, traffic, topo);
        double tSercon = core.calcTrafficCost(sercon, traffic, topo);
        double tProp = core.calcTrafficCost(proposed, traffic, topo);

        int hFfd = core.countActiveHosts(ffd);
        int hSercon = core.countActiveHosts(sercon);
        int hProp = core.countActiveHosts(proposed);

        double redFfd = (tFfd - tProp) / tFfd * 100.0;
        double redSercon = (tSercon - tProp) / tSercon * 100.0;

        System.out.println("=== 실험 1: 기존 알고리즘 vs 제안 기법 (Host 20 / VM 60) ===");
        System.out.printf("FFD      : traffic = %.2f, activeHosts = %d%n", tFfd, hFfd);
        System.out.printf("Sercon   : traffic = %.2f, activeHosts = %d%n", tSercon, hSercon);
        System.out.printf("Proposed : traffic = %.2f, activeHosts = %d%n", tProp, hProp);

        System.out.println();
        System.out.printf("→ Proposed vs FFD   감소율: %.2f%%%n", redFfd);
        System.out.printf("→ Proposed vs Sercon감소율: %.2f%%%n", redSercon);

        System.out.println("\n[포스터용 표]");
        System.out.println("---------------------------------------------");
        System.out.println("알고리즘   | 내부 트래픽 비용 | 사용 Host 수");
        System.out.println("---------------------------------------------");
        System.out.printf("FFD        | %12.2f   | %5d%n", tFfd, hFfd);
        System.out.printf("Sercon     | %12.2f   | %5d%n", tSercon, hSercon);
        System.out.printf("Proposed   | %12.2f   | %5d%n", tProp, hProp);
        System.out.println("---------------------------------------------\n");
    }
}
