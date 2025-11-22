package doit;

public class Main {
    public static void main(String[] args) {

        System.out.println("=============================================");
        new ExperimentAlgorithms().run();            // 실험 1: 기존 알고리즘 vs Proposed
        System.out.println("=============================================\n");

        new ExperimentVmScaling().run();             // 실험 2: VM 수 변화 (이제 Proposed만)
        System.out.println("=============================================\n");

        new ExperimentSupernodePercent().run();      // 실험 3: super-node 비율 (Proposed만)
        System.out.println("=============================================\n");

        new ExperimentTopologyProposed().run();      // 실험 4: 토폴로지별 (Proposed만)
        System.out.println("=============================================\n");
    }
}
