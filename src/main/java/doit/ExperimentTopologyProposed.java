package doit;

import java.util.List;
import java.util.Map;

public class ExperimentTopologyProposed {

    private final TrafficSimulationCore core = new TrafficSimulationCore();

    public void run() {
        int hostCount = 20;
        int vmCount   = 60;
        double supernodePercentile = 0.85;   // 고정값 (내부 파라미터 하나 고정해놓고 비교)

        TrafficSimulationCore.Topology[] topos = {
                TrafficSimulationCore.Topology.TREE,
                TrafficSimulationCore.Topology.FAT_TREE,
                TrafficSimulationCore.Topology.VL2
        };

        System.out.println("=== 실험 4: 네트워크 토폴로지별 제안 기법 성능 ===");
        System.out.printf("환경: Host=%d, VM=%d, super-node 상위 비율=%.2f%n",
                hostCount, vmCount, supernodePercentile);

        List<TrafficSimulationCore.HostInfo> hostsBase = core.createHostList(hostCount);
        List<TrafficSimulationCore.VmInfo>  vmsBase    = core.createVmList(vmCount);
        double[][] traffic = core.createTrafficMatrix(vmCount);

        double[] trafficValues = new double[topos.length];

        for (int i = 0; i < topos.length; i++) {
            TrafficSimulationCore.Topology topo = topos[i];

            List<TrafficSimulationCore.HostInfo> hosts = core.copyHosts(hostsBase);
            List<TrafficSimulationCore.VmInfo>  vms   = core.copyVms(vmsBase);

            Map<TrafficSimulationCore.VmInfo, TrafficSimulationCore.HostInfo> proposed =
                    core.placeVmsProposed(hosts, vms, traffic, supernodePercentile, topo);

            double tProp = core.calcTrafficCost(proposed, traffic, topo);
            trafficValues[i] = tProp;

            System.out.printf(" - Topology=%s → Proposed traffic = %.2f%n", topo, tProp);
        }

        double base = trafficValues[0]; // TREE를 기준으로

        System.out.println("\n[포스터용: 토폴로지별 제안 기법 내부 트래픽]");
        System.out.println("Topology | Proposed 내부 트래픽 | TREE 대비 변화율");
        System.out.println("------------------------------------------------");
        for (int i = 0; i < topos.length; i++) {
            String name = topos[i].name();
            double t = trafficValues[i];
            double diff = (base - t) / base * 100.0;  // +면 TREE보다 감소

            System.out.printf("%-8s | %21.2f | %8.2f%%%n", name, t, diff);
        }
        System.out.println("------------------------------------------------\n");
    }
}
