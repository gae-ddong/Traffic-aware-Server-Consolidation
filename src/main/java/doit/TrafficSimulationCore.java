package doit;

import java.util.*;

public class TrafficSimulationCore {

    public enum Topology {
        TREE, FAT_TREE, VL2
    }

    /** Host 정보 */
    public static class HostInfo {
        public final int id;
        public final int rackId;
        public final int podId;    // fat-tree용 (VL2/TREE에서는 대충 그룹용으로만 사용)
        public final long totalRam;
        public final long totalMips;
        public long remainingRam;
        public long remainingMips;

        public HostInfo(int id, int rackId, int podId, long totalRam, long totalMips) {
            this.id = id;
            this.rackId = rackId;
            this.podId = podId;
            this.totalRam = totalRam;
            this.totalMips = totalMips;
            this.remainingRam = totalRam;
            this.remainingMips = totalMips;
        }

        public long usedRam()  { return totalRam  - remainingRam; }
        public long usedMips() { return totalMips - remainingMips; }
    }

    /** VM 정보 */
    public static class VmInfo {
        public final int id;
        public final long ram;
        public final long mips;

        public VmInfo(int id, long ram, long mips) {
            this.id = id;
            this.ram = ram;
            this.mips = mips;
        }
    }

    // ==========================================================
    //  HOST / VM / TRAFFIC 생성
    // ==========================================================

    public List<HostInfo> createHostList(int hostCount) {
        List<HostInfo> hosts = new ArrayList<>();
        long totalRam = 64_000;
        long totalMips = 40_000;

        int racks = Math.max(1, hostCount / 4);   // 대충 4대당 1 rack
        int pods  = Math.max(1, racks / 2);       // 2 rack당 1 pod

        for (int i = 0; i < hostCount; i++) {
            int rackId = i % racks;
            int podId  = rackId / Math.max(1, racks / pods);
            hosts.add(new HostInfo(i, rackId, podId, totalRam, totalMips));
        }
        return hosts;
    }

    public List<VmInfo> createVmList(int vmCount) {
        Random r = new Random(1);
        List<VmInfo> list = new ArrayList<>();

        for (int i = 0; i < vmCount; i++) {
            long ram  = 1_000 + r.nextInt(7_000);
            long mips = 1_000 + r.nextInt(5_000);
            list.add(new VmInfo(i, ram, mips));
        }
        return list;
    }

    public List<HostInfo> copyHosts(List<HostInfo> list) {
        List<HostInfo> res = new ArrayList<>();
        for (HostInfo h : list) {
            HostInfo n = new HostInfo(h.id, h.rackId, h.podId, h.totalRam, h.totalMips);
            n.remainingRam = h.remainingRam;
            n.remainingMips = h.remainingMips;
            res.add(n);
        }
        return res;
    }

    public List<VmInfo> copyVms(List<VmInfo> list) {
        List<VmInfo> res = new ArrayList<>();
        for (VmInfo v : list) {
            res.add(new VmInfo(v.id, v.ram, v.mips));
        }
        return res;
    }

    public double[][] createTrafficMatrix(int n) {
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

    public double[][] createTrafficMatrixClustered(int vmCount, int groupCount) {
        double[][] t = new double[vmCount][vmCount];
        Random r = new Random(2);

        // 간단하게: VM i는 groupId = i % groupCount
        int[] group = new int[vmCount];
        for (int i = 0; i < vmCount; i++) {
            group[i] = i % groupCount;
        }

        for (int i = 0; i < vmCount; i++) {
            for (int j = i + 1; j < vmCount; j++) {
                double val;
                if (group[i] == group[j]) {
                    // 같은 그룹: 강한 트래픽 (ex. 100~200 사이)
                    val = 100 + r.nextDouble() * 100;
                } else {
                    // 다른 그룹: 약한 트래픽 (ex. 0~5 사이)
                    val = r.nextDouble() * 5;
                }
                t[i][j] = t[j][i] = val;
            }
        }
        return t;
    }


    // ==========================================================
    //  거리 모델 (Topology별)
    // ==========================================================

    public double dist(HostInfo a, HostInfo b, Topology topo) {
        if (a.id == b.id) return 0.0;

        switch (topo) {
            case TREE:
                if (a.rackId == b.rackId) return 1.0;
                return 20.0;
            case FAT_TREE:
                if (a.rackId == b.rackId) return 1.0;
                if (a.podId == b.podId) return 5.0;
                return 20.0;
            case VL2:
                if (a.rackId == b.rackId) return 1.0;
                return 5.0;
            default:
                return 20.0;
        }
    }

    public int countActiveHosts(Map<VmInfo, HostInfo> pm) {
        return new HashSet<>(pm.values()).size();
    }

    public double calcTrafficCost(Map<VmInfo, HostInfo> pm, double[][] t, Topology topo) {
        double total = 0.0;
        List<VmInfo> vms = new ArrayList<>(pm.keySet());

        for (int i = 0; i < vms.size(); i++) {
            for (int j = i + 1; j < vms.size(); j++) {
                VmInfo a = vms.get(i);
                VmInfo b = vms.get(j);
                HostInfo ha = pm.get(a);
                HostInfo hb = pm.get(b);
                if (ha == null || hb == null) continue;
                total += t[a.id][b.id] * dist(ha, hb, topo);
            }
        }
        return total;
    }

    // ==========================================================
    //  (1) FFD
    // ==========================================================

    public Map<VmInfo, HostInfo> placeVmsFFD(List<HostInfo> hosts, List<VmInfo> vms) {
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

    // ==========================================================
    //  (2) Sercon (간단 구현)
    // ==========================================================

    public Map<VmInfo, HostInfo> placeVmsSercon(List<HostInfo> hosts, List<VmInfo> vms) {
        Map<VmInfo, HostInfo> placement = new HashMap<>();

        // 초기 FFD 배치
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

        // 클러스터 부하 계산
        double clusterCpu = 0, clusterMem = 0;
        for (HostInfo h : hosts) {
            clusterCpu += (double) h.usedMips() / h.totalMips;
            clusterMem += (double) h.usedRam() / h.totalRam;
        }
        double lambda = clusterCpu / (clusterCpu + clusterMem + 1e-9);

        // Host load 기준 정렬 (낮은 부하 → 비울 타겟)
        List<HostInfo> sortedHosts = new ArrayList<>(hosts);
        Comparator<HostInfo> loadCmp = (a, b) -> {
            double la = lambda * ((double)a.usedMips()/a.totalMips)
                    + (1-lambda) * ((double)a.usedRam()/a.totalRam);
            double lb = lambda * ((double)b.usedMips()/b.totalMips)
                    + (1-lambda) * ((double)b.usedRam()/b.totalRam);
            return Double.compare(la, lb);
        };
        sortedHosts.sort(loadCmp);

        for (HostInfo target : sortedHosts) {
            List<VmInfo> inside = new ArrayList<>();
            for (Map.Entry<VmInfo, HostInfo> e : placement.entrySet()) {
                if (e.getValue().id == target.id) inside.add(e.getKey());
            }
            if (inside.isEmpty()) continue;

            List<HostInfo> candidates = new ArrayList<>(hosts);
            candidates.remove(target);
            candidates.sort((a, b) -> -loadCmp.compare(a, b)); // 부하 높은 순

            boolean allMoved = true;
            for (VmInfo vm : inside) {
                boolean placed = false;
                for (HostInfo h : candidates) {
                    if (h.remainingRam >= vm.ram && h.remainingMips >= vm.mips) {
                        placement.put(vm, h);
                        target.remainingRam += vm.ram;
                        target.remainingMips += vm.mips;
                        h.remainingRam -= vm.ram;
                        h.remainingMips -= vm.mips;
                        placed = true;
                        break;
                    }
                }
                if (!placed) {
                    allMoved = false;
                    break;
                }
            }
            if (allMoved) {
                System.out.println("[Sercon] Host " + target.id + " emptied.");
            }
        }
        return placement;
    }

    // ==========================================================
    //  (3) Proposed: S_i + Traffic-aware Partitioning
    // ==========================================================

    private HostInfo selectReleaseCandidateForProposed(List<HostInfo> hosts) {
        double bestScore = -1;
        HostInfo best = null;

        for (HostInfo h : hosts) {
            double uCpu = (double) h.usedMips() / h.totalMips;
            double uMem = (double) h.usedRam() / h.totalRam;

            double U = 1 - (uCpu + uMem) / 2; // 남은 자원 비율
            double B = (Math.max(uCpu, uMem) == 0)
                    ? 1.0
                    : 1.0 - Math.abs(uCpu - uMem) / Math.max(uCpu, uMem);
            double R = (uCpu == 0 || uMem == 0)
                    ? 0.0
                    : Math.min(1 - uCpu, 1 - uMem) / Math.max(uCpu, uMem);

            double S = 0.5 * U + 0.3 * B + 0.2 * R;

            if (S > bestScore) {
                bestScore = S;
                best = h;
            }
        }
        return best;
    }

    private List<List<VmInfo>> partitionCandidateHostVms(
            List<VmInfo> vms, double[][] traffic, int k, double percentile) {

        int n = vms.size();
        boolean[] visited = new boolean[n];

        double[] edges = new double[n * n];
        int idx = 0;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                edges[idx++] = traffic[vms.get(i).id][vms.get(j).id];

        Arrays.sort(edges, 0, idx);
        double threshold = edges[(int)(idx * percentile)];

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

        // 너무 많은 클러스터면 작은 것부터 합치기
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
            long ram = 0, mips = 0;
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
            double[][] traffic,
            Topology topo) {

        long ram = 0, mips = 0;
        for (VmInfo v : part) {
            ram += v.ram;
            mips += v.mips;
        }

        HostInfo bestHost = null;
        double bestCost = Double.MAX_VALUE;

        for (HostInfo h : hosts) {
            if (h == exclude) continue;
            if (h.remainingRam < ram || h.remainingMips < mips) continue;

            double cost = 0.0;

            for (VmInfo v : part) {
                for (Map.Entry<VmInfo, HostInfo> e : placement.entrySet()) {
                    VmInfo other = e.getKey();
                    HostInfo otherHost = e.getValue();
                    if (part.contains(other)) continue;

                    double t = traffic[v.id][other.id];
                    double d = dist(h, otherHost, topo);
                    cost += t * d;
                }
            }

            if (cost < bestCost) {
                bestCost = cost;
                bestHost = h;
            }
        }

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

    // 새 버전: 이미 해제 시도한 host(tried)에 속한 건 제외
    public HostInfo selectReleaseCandidateForProposed(
            List<HostInfo> hosts,
            Set<Integer> tried) {

        double bestScore = -1;
        HostInfo best = null;

        for (HostInfo h : hosts) {
            if (tried.contains(h.id)) continue;

            double uCpu = (double) h.usedMips() / h.totalMips;
            double uMem = (double) h.usedRam() / h.totalRam;

            double U = 1 - (uCpu + uMem) / 2;
            double B = (Math.max(uCpu, uMem) == 0)
                    ? 1
                    : 1 - Math.abs(uCpu - uMem) / Math.max(uCpu, uMem);
            double R = (uCpu == 0 || uMem == 0)
                    ? 0
                    : Math.min((1 - uCpu), (1 - uMem)) / Math.max(uCpu, uMem);

            double S = 0.5 * U + 0.3 * B + 0.2 * R;

            if (S > bestScore) {
                bestScore = S;
                best = h;
            }
        }
        return best;
    }


    public Map<VmInfo, HostInfo> placeVmsProposed(
            List<HostInfo> hosts,
            List<VmInfo> vms,
            double[][] traffic,
            double supernodePercentile,
            Topology topo) {

        Map<VmInfo, HostInfo> placement = new HashMap<>();

        // ------------------------------------------------------
        // Phase 0: 초기 FFD 배치 (지금까지랑 동일)
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

        // 초기 전체 트래픽 비용
        double currentCost = calcTrafficCost(placement, traffic, topo);

        // ------------------------------------------------------
        // Phase 1~4: 여러 Host를 순차적으로 해제 시도하되
        //            "더 나빠지는 이동은 reject"
        // ------------------------------------------------------
        final int MAX_RELEASE = 3;              // 최대 해제 시도 서버 수
        Set<Integer> triedHosts = new HashSet<>();

        for (int iter = 0; iter < MAX_RELEASE; iter++) {

            // 1) 아직 시도 안 한 host들 중 S_i가 가장 큰 candidate 선택
            HostInfo candidate = selectReleaseCandidateForProposed(hosts, triedHosts);
            if (candidate == null) break;
            triedHosts.add(candidate.id);

            // 2) candidate host에 올라간 VM 목록
            List<VmInfo> candidateVms = new ArrayList<>();
            for (Map.Entry<VmInfo, HostInfo> e : placement.entrySet()) {
                if (e.getValue().id == candidate.id) {
                    candidateVms.add(e.getKey());
                }
            }

            if (candidateVms.isEmpty()) {
                // 이미 비어있으면 다음 host로
                continue;
            }

            // --------------------------------------------------
            // [중요] 여기부터는 "시뮬레이션 모드"에서 먼저 해본다
            //  hosts / placement의 복사본을 만든 뒤,
            //  그 위에서 파티셔닝 + migration 실행 → newCost 계산
            // --------------------------------------------------

            // (a) Host 복사
            List<HostInfo> simHosts = copyHosts(hosts);  // 기존에 있던 copyHosts 재사용
            Map<Integer, HostInfo> idToSimHost = new HashMap<>();
            for (HostInfo h : simHosts) {
                idToSimHost.put(h.id, h);
            }

            // (b) Placement 복사 (Vm -> Host 매핑을 복사된 host로 바꿔줌)
            Map<VmInfo, HostInfo> simPlacement = new HashMap<>();
            for (Map.Entry<VmInfo, HostInfo> e : placement.entrySet()) {
                VmInfo vm = e.getKey();
                HostInfo oldHost = e.getValue();
                HostInfo simHost = idToSimHost.get(oldHost.id);
                simPlacement.put(vm, simHost);
            }

            HostInfo simCandidate = idToSimHost.get(candidate.id);

            // --------------------------------------------------
            // (c) Traffic-aware 파티셔닝 (k를 늘려가며 시도)
            // --------------------------------------------------
            int k = 2;
            List<List<VmInfo>> partitions = null;

            while (true) {
                partitions = partitionCandidateHostVms(
                        candidateVms, traffic, k, supernodePercentile);

                boolean ok = canMigrateAllPartitions(partitions, simHosts, simCandidate);
                if (ok) break;

                k++;
                if (k > candidateVms.size()) {
                    partitions = null; // 이 host는 해제 불가
                    break;
                }
            }

            if (partitions == null) {
                System.out.println("[Proposed] Host " + candidate.id +
                        " cannot be fully released (capacity constraint).");
                continue;
            }

            // --------------------------------------------------
            // (d) 시뮬레이션 상에서 각 파티션 migration 실행
            // --------------------------------------------------
            for (List<VmInfo> part : partitions) {
                migratePartition(part, simHosts, simPlacement, simCandidate, traffic, topo);
            }

            // (e) 시뮬레이션 결과 트래픽 비용 계산
            double newCost = calcTrafficCost(simPlacement, traffic, topo);

            // --------------------------------------------------
            // (f) 더 좋아졌으면 → 실제 배치에 반영 (accept)
            //     더 나빠졌으면 → 버리고 넘어감 (reject)
            // --------------------------------------------------
            if (newCost < currentCost) {
                // accept: 실제 hosts/placement를 시뮬레이션 결과로 교체
                placement = simPlacement;
                hosts.clear();
                hosts.addAll(simHosts);
                currentCost = newCost;
                System.out.println("[Proposed] Host " + candidate.id +
                        " successfully released (accepted, cost improved).");
            } else {
                System.out.println("[Proposed] Host " + candidate.id +
                        " release rejected (cost increased: " +
                        String.format("%.2f -> %.2f", currentCost, newCost) + ")");
            }
        }

        return placement;
    }


}
