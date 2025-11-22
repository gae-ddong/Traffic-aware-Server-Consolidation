package doit;

import java.util.List;
import java.util.Map;

public class ExperimentVmScaling {

    private final TrafficSimulationCore core = new TrafficSimulationCore();

    public void run() {
        // (Host, VM) 스케일 설정들
        int[][] configs = {
                {20, 60},    // 실험 B-1: 소규모
                {40, 120},   // 실험 B-2: 중간 규모
                {60, 180}    // 실험 B-3: 대규모
        };

        double supernodePercentile = 0.85; // super-node 상위 edge 비율
        TrafficSimulationCore.Topology topo = TrafficSimulationCore.Topology.TREE;

        System.out.println("=== 실험 2: VM 수 변화에 따른 제안 기법 성능 ===");
        System.out.printf("Topology = %s, super-node 상위 비율 = %.2f%n",
                topo, supernodePercentile);

        // 각 스케일에서의 Proposed 트래픽 값을 저장 (비교용)
        double[] proposedTraffic = new double[configs.length];

        for (int i = 0; i < configs.length; i++) {
            int hostCount = configs[i][0];
            int vmCount   = configs[i][1];

            // 매 실험마다 새로 환경 생성
            List<TrafficSimulationCore.HostInfo> hostsBase = core.createHostList(hostCount);
            List<TrafficSimulationCore.VmInfo>  vmsBase    = core.createVmList(vmCount);
            double[][] traffic = core.createTrafficMatrix(vmCount);

            // 제안 기법 실행
            Map<TrafficSimulationCore.VmInfo, TrafficSimulationCore.HostInfo> proposed =
                    core.placeVmsProposed(
                            core.copyHosts(hostsBase),
                            core.copyVms(vmsBase),
                            traffic,
                            supernodePercentile,
                            topo
                    );

            double tProp = core.calcTrafficCost(proposed, traffic, topo);
            int    hProp = core.countActiveHosts(proposed);

            proposedTraffic[i] = tProp;

            String label = "B-" + (i + 1);
            System.out.printf(
                    "\n[실험 %s] Host=%d, VM=%d%n", label, hostCount, vmCount);
            System.out.printf(
                    "  → Proposed: traffic = %.2f, activeHosts = %d%n", tProp, hProp);
        }

        // 맨 첫 번째 스케일(B-1)을 기준으로 상대 변화율 계산 (포스터용)
        double base = proposedTraffic[0];

        System.out.println("\n[포스터용 요약 표: VM 수 증가에 따른 제안 기법 내부 트래픽]");
        System.out.println("실험 | Host/VM     | Proposed 트래픽 | B-1 대비 변화율");
        System.out.println("------------------------------------------------------");
        for (int i = 0; i < configs.length; i++) {
            int hostCount = configs[i][0];
            int vmCount   = configs[i][1];
            String label  = "B-" + (i + 1);
            double t      = proposedTraffic[i];
            double diff   = (t - base) / base * 100.0; // +면 B-1보다 증가

            System.out.printf(
                    "%-4s | %3d / %-4d | %15.2f | %8.2f%%%n",
                    label, hostCount, vmCount, t, diff);
        }
        System.out.println("------------------------------------------------------\n");
    }
}
