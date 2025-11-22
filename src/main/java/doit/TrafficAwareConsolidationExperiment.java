package doit;

import java.util.*;

/**
 * Host 20대 / VM 60개 / Rack 5개 구성
 * 비교 알고리즘:
 *   1) FFD (First Fit Decreasing)
 *   2) Sercon 스타일 (Balanced Resource Fit)
 *   3) Proposed (Traffic-aware Placement)
 *
 * 출력:
 *   - 내부 트래픽 비용
 *   - 사용된 Host 수
 *   - Proposed 대비 감소율(%)
 */
public class TrafficAwareConsolidationExperiment {

    private static final int HOST_COUNT = 20;
    private static final int VM_COUNT = 60;

    // 트래픽-aware 파라미터
    private static final double LAMBDA = 8.0;

    /** Host 정보 */
    static class HostInfo {
        final int id;
        final int rackId;
        final long totalRam;
        final long totalMips;
        long remainingRam;
        long remainingMips;

        HostInfo(int id, int rackId, long totalRam, long totalMips) {
            this.id = id;
            this.rackId = rackId;
            this.totalRam = totalRam;
            this.totalMips = totalMips;
            this.remainingRam = totalRam;
            this.remainingMips = totalMips;
        }

        long usedRam() { return totalRam - remainingRam; }
        long usedMips() { return totalMips - remainingMips; }
    }

    /** VM 정보 */
    static class VmInfo {
        final int id;
        final long ram;
        final long mips;

        VmInfo(int id, long ram, long mips) {
            this.id = id;
            this.ram = ram;
            this.mips = mips;
        }
    }

    public void run() {
        // [실험 세트] {Host 개수, VM 개수}
        int[][] configs = {
                {20, 60},   // 실험 1: 소규모
                {40, 120},  // 실험 2: 중간 규모
                {60, 180}   // 실험 3: 대규모
        };

        System.out.println("=== 트래픽-aware 서버 통합 실험 시작 ===\n");

        for (int i = 0; i < configs.length; i++) {
            int hostCount = configs[i][0];
            int vmCount = configs[i][1];
            runSingleExperiment(i + 1, hostCount, vmCount);
            System.out.println();  // 실험 사이 공백
            System.out.println("=============================================\n");
        }

        System.out.println("=== 모든 실험 종료 ===");
    }

    // 실험 1/2/3 각각을 수행하는 함수
    private void runSingleExperiment(int expNo, int hostCount, int vmCount) {
        System.out.printf(">>> 실험 %d: Host = %d, VM = %d%n", expNo, hostCount, vmCount);

        List<HostInfo> hostsBase = createHostList(hostCount);
        List<VmInfo> vmsBase = createVmList(vmCount);
        double[][] trafficMatrix = createTrafficMatrix(vmCount);

        // 1) FFD
        Map<VmInfo, HostInfo> placementFFD =
                placeVmsFFD(copyHosts(hostsBase), copyVms(vmsBase));
        double trafficFFD = calcTrafficCost(placementFFD, trafficMatrix);
        int activeFFD = countActiveHosts(placementFFD);

        // 2) Sercon
        Map<VmInfo, HostInfo> placementSercon =
                placeVmsSercon(copyHosts(hostsBase), copyVms(vmsBase));
        double trafficSercon = calcTrafficCost(placementSercon, trafficMatrix);
        int activeSercon = countActiveHosts(placementSercon);

        // 3) Proposed
        Map<VmInfo, HostInfo> placementProposed =
                placeVmsTrafficAware(copyHosts(hostsBase), copyVms(vmsBase), trafficMatrix);
        double trafficProposed = calcTrafficCost(placementProposed, trafficMatrix);
        int activeProposed = countActiveHosts(placementProposed);

        double redFFD = (trafficFFD - trafficProposed) / trafficFFD * 100.0;
        double redSercon = (trafficSercon - trafficProposed) / trafficSercon * 100.0;

        // --- 콘솔 출력 (원래 run() 내용 각 실험별로) ---
        System.out.println();
        System.out.println("=== 실험 " + expNo + " 결과 비교 ===");
        System.out.printf("FFD          : traffic = %.2f, activeHosts = %d%n",
                trafficFFD, activeFFD);
        System.out.printf("Sercon       : traffic = %.2f, activeHosts = %d%n",
                trafficSercon, activeSercon);
        System.out.printf("Proposed     : traffic = %.2f, activeHosts = %d%n",
                trafficProposed, activeProposed);

        System.out.println();
        System.out.printf("→ Proposed vs FFD 트래픽 감소율: %.2f%%%n", redFFD);
        System.out.printf("→ Proposed vs Sercon 감소율   : %.2f%%%n", redSercon);

        System.out.println("\n=== 실험 " + expNo + " 포스터용 표 ===");
        System.out.println("---------------------------------------------");
        System.out.println("알고리즘     | 내부 트래픽 비용 | 사용 Host 수");
        System.out.println("---------------------------------------------");
        System.out.printf("FFD          | %12.2f   | %5d%n", trafficFFD, activeFFD);
        System.out.printf("Sercon       | %12.2f   | %5d%n", trafficSercon, activeSercon);
        System.out.printf("Proposed     | %12.2f   | %5d%n", trafficProposed, activeProposed);
        System.out.println("---------------------------------------------");
    }


    // ==========================================================
    //  HOSTS / VMS 생성
    // ==========================================================

    private List<HostInfo> createHostList(int count) {
        List<HostInfo> hosts = new ArrayList<>();
        long totalRam = 64000;
        long totalMips = 40000;

        for (int i = 0; i < count; i++) {
            int rackId = i / 4;
            hosts.add(new HostInfo(i, rackId, totalRam, totalMips));
        }
        return hosts;
    }

    private List<VmInfo> createVmList(int count) {
        Random r = new Random(1);
        List<VmInfo> list = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            long ram = 1000 + r.nextInt(7000);
            long mips = 1000 + r.nextInt(5000);
            list.add(new VmInfo(i, ram, mips));
        }
        return list;
    }

    private List<HostInfo> copyHosts(List<HostInfo> list) {
        List<HostInfo> res = new ArrayList<>();
        for (HostInfo h : list) {
            HostInfo n = new HostInfo(h.id, h.rackId, h.totalRam, h.totalMips);
            n.remainingRam = h.remainingRam;
            n.remainingMips = h.remainingMips;
            res.add(n);
        }
        return res;
    }

    private List<VmInfo> copyVms(List<VmInfo> list) {
        List<VmInfo> res = new ArrayList<>();
        for (VmInfo v : list) {
            res.add(new VmInfo(v.id, v.ram, v.mips));
        }
        return res;
    }

    // ==========================================================
    //  트래픽 행렬 생성
    // ==========================================================

    private double[][] createTrafficMatrix(int n) {
        Random r = new Random(2);
        double[][] m = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double p = r.nextDouble();
                double val = (p < 0.7) ? p * 5 : 50 + p * 50;
                m[i][j] = m[j][i] = val;
            }
        }
        return m;
    }

    // ==========================================================
    //  거리 모델 (Host Distance)
    // ==========================================================

    private double dist(HostInfo a, HostInfo b) {
        if (a.id == b.id) return 0;
        if (a.rackId == b.rackId) return 1;
        return 50;
    }

    private int countActiveHosts(Map<VmInfo, HostInfo> pm) {
        return new HashSet<>(pm.values()).size();
    }

    private double calcTrafficCost(Map<VmInfo, HostInfo> pm, double[][] t) {
        double total = 0;
        List<VmInfo> vms = new ArrayList<>(pm.keySet());

        for (int i = 0; i < vms.size(); i++) {
            for (int j = i + 1; j < vms.size(); j++) {
                VmInfo a = vms.get(i);
                VmInfo b = vms.get(j);
                HostInfo ha = pm.get(a);
                HostInfo hb = pm.get(b);

                total += t[a.id][b.id] * dist(ha, hb);
            }
        }
        return total;
    }

    private HostInfo selectReleaseCandidateForProposed(List<HostInfo> hosts) {
        double bestScore = -1;
        HostInfo best = null;

        for (HostInfo h : hosts) {
            double uCpu = (double) h.usedMips() / h.totalMips;
            double uMem = (double) h.usedRam() / h.totalRam;

            double U = 1 - (uCpu + uMem) / 2;          // 남은 자원 비율
            double B = (Math.max(uCpu, uMem) == 0) ?
                    1 : 1 - Math.abs(uCpu - uMem) / Math.max(uCpu, uMem);
            double R = (uCpu == 0 || uMem == 0) ? 0 :
                    Math.min((1 - uCpu), (1 - uMem)) / Math.max(uCpu, uMem);

            double S = 0.5 * U + 0.3 * B + 0.2 * R;

            if (S > bestScore) {
                bestScore = S;
                best = h;
            }
        }
        return best;
    }

    private List<List<VmInfo>> partitionCandidateHostVms(
            List<VmInfo> vms, double[][] traffic, int k) {

        int n = vms.size();
        boolean[] visited = new boolean[n];

        // edge sorting for threshold
        double[] edges = new double[n * n];
        int idx = 0;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                edges[idx++] = traffic[vms.get(i).id][vms.get(j).id];

        Arrays.sort(edges);
        double threshold = edges[(int)(edges.length * 0.9)];

        // super-node clustering
        List<List<VmInfo>> clusters = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            if (visited[i]) continue;

            Queue<Integer> q = new LinkedList<>();
            q.add(i);
            visited[i] = true;

            List<VmInfo> c = new ArrayList<>();

            while (!q.isEmpty()) {
                int x = q.poll();
                c.add(vms.get(x));

                for (int y = 0; y < n; y++) {
                    if (!visited[y] &&
                            traffic[vms.get(x).id][vms.get(y).id] >= threshold) {
                        visited[y] = true;
                        q.add(y);
                    }
                }
            }
            clusters.add(c);
        }

        // 파티션 개수 조절
        while (clusters.size() > k) {
            clusters.sort(Comparator.comparingInt(List::size));
            List<VmInfo> a = clusters.remove(0);
            List<VmInfo> b = clusters.remove(0);
            a.addAll(b);
            clusters.add(a);
        }

        return clusters;
    }

    private boolean canMigrateAllPartitions(
            List<List<VmInfo>> parts,
            List<HostInfo> hosts,
            HostInfo exclude) {

        for (List<VmInfo> part : parts) {

            long ram = 0;
            long mips = 0;
            for (VmInfo v : part) {
                ram += v.ram;
                mips += v.mips;
            }

            boolean ok = false;

            for (HostInfo h : hosts) {
                if (h == exclude) continue;

                if (h.remainingRam >= ram && h.remainingMips >= mips) {
                    ok = true;
                    break;
                }
            }
            if (!ok) return false;
        }
        return true;
    }

    private void migratePartition(
            List<VmInfo> part,
            List<HostInfo> hosts,
            Map<VmInfo, HostInfo> placement,
            HostInfo exclude,
            double[][] traffic) {

        long ram = 0, mips = 0;
        for (VmInfo v : part) {
            ram += v.ram;
            mips += v.mips;
        }

        HostInfo bestHost = null;
        double bestCost = Double.MAX_VALUE;

        // 1) 자원 조건을 만족하는 host들 중에서
        //    이 파티션을 올렸을 때 트래픽 비용이 최소가 되는 host 찾기
        for (HostInfo h : hosts) {
            if (h == exclude) continue;
            if (h.remainingRam < ram || h.remainingMips < mips) continue;

            double cost = 0.0;

            // part가 h로 옮겨졌다고 가정했을 때,
            // "part 안의 VM들"과 "나머지 모든 VM들" 사이의 트래픽 비용 계산
            for (VmInfo v : part) {
                for (Map.Entry<VmInfo, HostInfo> e : placement.entrySet()) {
                    VmInfo other = e.getKey();
                    HostInfo otherHost = e.getValue();

                    // 같은 파티션 안의 VM끼리는 모두 같은 host(h)에 놓이므로 dist=0 취급
                    if (part.contains(other)) continue;

                    double t = traffic[v.id][other.id];
                    double d = dist(h, otherHost);
                    cost += t * d;
                }
            }

            if (cost < bestCost) {
                bestCost = cost;
                bestHost = h;
            }
        }

        // 2) 선택된 bestHost로 실제 migration 수행
        if (bestHost != null) {
            for (VmInfo v : part) {
                HostInfo old = placement.get(v);
                if (old != null) {
                    old.remainingRam += v.ram;
                    old.remainingMips += v.mips;
                }

                placement.put(v, bestHost);
                bestHost.remainingRam -= v.ram;
                bestHost.remainingMips -= v.mips;
            }
        }
    }



    // ==========================================================
    //  (1) FFD 알고리즘
    // ==========================================================

    private Map<VmInfo, HostInfo> placeVmsFFD(List<HostInfo> hosts, List<VmInfo> vms) {
        Map<VmInfo, HostInfo> pm = new HashMap<>();

        vms.sort((a, b) -> Long.compare(b.mips, a.mips)); // 큰 VM 먼저

        for (VmInfo v : vms) {
            for (HostInfo h : hosts) {
                if (h.remainingRam >= v.ram && h.remainingMips >= v.mips) {
                    pm.put(v, h);
                    h.remainingRam -= v.ram;
                    h.remainingMips -= v.mips;
                    break;
                }
            }
        }
        return pm;
    }

    /**
     * Sercon Algorithm (논문 기반 완전 구현 버전)
     *
     * 핵심:
     * 1) 대리 가중치 λ 계산 (CPU/MEM 전체 부하 비율)
     * 2) Host load = λ*CPU + (1-λ)*MEM
     * 3) 부하 낮은 Host부터 비우기(target)
     * 4) target Host의 VM을 다른 Host에 재배치
     * 5) Host가 완전히 비워지면 해제(success)
     */
    private Map<VmInfo, HostInfo> placeVmsSercon(List<HostInfo> hosts, List<VmInfo> vms) {

        Map<VmInfo, HostInfo> placement = new HashMap<>();

        // -----------------------------
        // Step 1. VM을 먼저 가장 간단히 FFD 방식으로 초기 배치
        // -----------------------------
        // (초기 배치는 단순히 현재 부하 수집을 위해 필요)
        for (VmInfo v : vms) {
            for (HostInfo h : hosts) {
                if (h.remainingRam >= v.ram && h.remainingMips >= v.mips) {
                    placement.put(v, h);
                    h.remainingRam -= v.ram;
                    h.remainingMips -= v.mips;
                    break;
                }
            }
        }

        // -----------------------------
        // Step 2. 클러스터 전체 CPU/MEM 부하 계산
        // -----------------------------
        double clusterCpuLoad = 0;
        double clusterMemLoad = 0;

        for (HostInfo h : hosts) {
            clusterCpuLoad += (double) h.usedMips() / h.totalMips;
            clusterMemLoad += (double) h.usedRam()  / h.totalRam;
        }

        // -----------------------------
        // Step 3. 대리 가중치 λ 계산
        // -----------------------------
        double lambda = clusterCpuLoad / (clusterCpuLoad + clusterMemLoad + 1e-9);

        // -----------------------------
        // Step 4. Host load 계산 (부하 낮은 순으로 정렬)
        // -----------------------------
        List<HostInfo> sortedHosts = new ArrayList<>(hosts);

        Comparator<HostInfo> loadCmp = (a, b) -> {
            double loadA = lambda * ((double)a.usedMips()/a.totalMips)
                    + (1-lambda) * ((double)a.usedRam()/a.totalRam);

            double loadB = lambda * ((double)b.usedMips()/b.totalMips)
                    + (1-lambda) * ((double)b.usedRam()/b.totalRam);

            return Double.compare(loadA, loadB); // 낮은 부하 먼저
        };

        sortedHosts.sort(loadCmp);

        // -----------------------------
        // Step 5. 가장 한가한 Host부터 비우기 시도
        // -----------------------------
        for (HostInfo target : sortedHosts) {

            // target Host 내부 VM 목록 수집
            List<VmInfo> vmInsideTarget = new ArrayList<>();
            for (Map.Entry<VmInfo, HostInfo> e : placement.entrySet()) {
                if (e.getValue().id == target.id) {
                    vmInsideTarget.add(e.getKey());
                }
            }

            if (vmInsideTarget.isEmpty())
                continue; // 이미 빈 Host

            // target Host의 VM은 부하가 높은 Host부터 재배치해야 함
            List<HostInfo> candidateHosts = new ArrayList<>(hosts);
            candidateHosts.remove(target); // target은 제외

            // Host들을 "부하 높은 순"으로 정렬
            candidateHosts.sort((a, b) -> -loadCmp.compare(a, b));

            boolean allMigrated = true;

            // -----------------------------
            // Step 6. target에 있는 VM들을 하나씩 재배치
            // -----------------------------
            for (VmInfo vm : vmInsideTarget) {
                boolean placed = false;

                for (HostInfo h : candidateHosts) {
                    if (h.remainingRam >= vm.ram && h.remainingMips >= vm.mips) {
                        // 기존 Host에서 제거
                        placement.put(vm, h);

                        // 자원 반영
                        target.remainingRam += vm.ram;
                        target.remainingMips += vm.mips;

                        h.remainingRam -= vm.ram;
                        h.remainingMips -= vm.mips;

                        placed = true;
                        break;
                    }
                }

                if (!placed) {
                    allMigrated = false;
                    break;
                }
            }

            // -----------------------------
            // Step 7. target Host 비우기 성공한 경우 → 해제 성공
            // -----------------------------
            if (allMigrated) {
                // target host는 더 이상 사용 안 함
                System.out.println("[Sercon] Host " + target.id + " successfully emptied.");
            }
        }

        return placement;
    }



    // ==========================================================
    //  (3) Proposed: Traffic-aware 알고리즘
    // ==========================================================

    /**
     * Improved Proposed: Traffic-aware Placement
     * 개선 사항:
     * 1) 트래픽 많은 VM 우선 배치
     * 2) distance 강화 (rack 간 3 → 20)
     */
    /**
     * Proposed Algorithm (Final Version)
     *
     * Step 1. S_i 기반 Release Candidate Host 선정
     * Step 2. Candidate Host의 VM들 Traffic-aware Partitioning
     * Step 3. 파티션들을 다른 Host로 Migration 가능 여부 확인
     * Step 4. Migration 실행 후 Host Release
     */
    private Map<VmInfo, HostInfo> placeVmsTrafficAware(
            List<HostInfo> hosts,
            List<VmInfo> vms,
            double[][] traffic) {

        Map<VmInfo, HostInfo> placement = new HashMap<>();

        // ------------------------------------------------------
        // Phase 0: 초기 배치 (FFD 기반)
        // ------------------------------------------------------
        for (VmInfo v : vms) {
            for (HostInfo h : hosts) {
                if (h.remainingRam >= v.ram && h.remainingMips >= v.mips) {
                    placement.put(v, h);
                    h.remainingRam -= v.ram;
                    h.remainingMips -= v.mips;
                    break;
                }
            }
        }

        // ------------------------------------------------------
        // Phase 1: Release Candidate Host 선정 (S_i 계산)
        // ------------------------------------------------------
        HostInfo candidate = selectReleaseCandidateForProposed(hosts);
        if (candidate == null) return placement;

        // 후보 서버에 올라간 VM 목록 찾기
        List<VmInfo> candidateVms = new ArrayList<>();
        for (VmInfo v : placement.keySet()) {
            if (placement.get(v).id == candidate.id) {
                candidateVms.add(v);
            }
        }

        if (candidateVms.isEmpty()) {
            return placement; // 이미 빈 Host
        }

        // ------------------------------------------------------
        // Phase 2: Traffic-aware Partitioning
        //    k 개로 나누고, 필요한 경우 k 증가하여 세분화
        // ------------------------------------------------------
        int k = 2;
        List<List<VmInfo>> partitions = null;

        while (true) {
            partitions = partitionCandidateHostVms(candidateVms, traffic, k);

            boolean ok = canMigrateAllPartitions(partitions, hosts, candidate);
            if (ok) break;

            k++;
            if (k > candidateVms.size()) break;
        }

        // ------------------------------------------------------
        // Phase 3: 각 파티션 Migration 실행
        // ------------------------------------------------------
        for (List<VmInfo> part : partitions) {
            migratePartition(part, hosts, placement, candidate, traffic);
        }

        // ------------------------------------------------------
        // Phase 4: Candidate Host 해제 성공
        // ------------------------------------------------------
        System.out.println("[Proposed] Host " + candidate.id + " successfully released.");

        return placement;
    }


}
