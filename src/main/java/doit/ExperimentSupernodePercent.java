package doit;

import java.util.List;
import java.util.Map;

public class ExperimentSupernodePercent {

    private final TrafficSimulationCore core = new TrafficSimulationCore();

    public void run() {
        int hostCount = 20;
        int vmCount   = 60;

        // 제안 기법 내부 파라미터: super-node 상위 edge 비율
        double[] percentiles = {0.70, 0.85, 0.95};

        TrafficSimulationCore.Topology topo = TrafficSimulationCore.Topology.TREE;

        System.out.println("=== 실험 3: Super-node 기준 퍼센티지에 따른 제안 기법 성능 ===");
        System.out.printf("환경: Host=%d, VM=%d, 토폴로지=%s%n",
                hostCount, vmCount, topo);

        List<TrafficSimulationCore.HostInfo> hostsBase = core.createHostList(hostCount);
        List<TrafficSimulationCore.VmInfo>  vmsBase    = core.createVmList(vmCount);
        double[][] traffic = core.createTrafficMatrix(vmCount);

        // 각 퍼센티지별 결과 저장
        double[] trafficValues = new double[percentiles.length];

        for (int i = 0; i < percentiles.length; i++) {
            double p = percentiles[i];

            // 매 실험마다 host/vm/placement 초기화 필요
            List<TrafficSimulationCore.HostInfo> hosts = core.copyHosts(hostsBase);
            List<TrafficSimulationCore.VmInfo>  vms   = core.copyVms(vmsBase);

            Map<TrafficSimulationCore.VmInfo, TrafficSimulationCore.HostInfo> proposed =
                    core.placeVmsProposed(hosts, vms, traffic, p, topo);

            double tProp = core.calcTrafficCost(proposed, traffic, topo);
            trafficValues[i] = tProp;

            System.out.printf(" - percentile=%.2f → Proposed traffic = %.2f%n", p, tProp);
        }

        // 0.70을 기준으로 감소율 계산 (포스터용 설명에 쓰기 좋게)
        double base = trafficValues[0];

        System.out.println("\n[포스터용: Super-node 비율에 따른 내부 트래픽]");
        System.out.println("비율(p) | Proposed 내부 트래픽 | p=0.70 대비 감소율");
        System.out.println("-------------------------------------------------");
        for (int i = 0; i < percentiles.length; i++) {
            double p = percentiles[i];
            double t = trafficValues[i];
            double red = (base - t) / base * 100.0;
            System.out.printf("%.2f    | %18.2f | %8.2f%%%n", p, t, red);
        }
        System.out.println("-------------------------------------------------\n");
    }
}
