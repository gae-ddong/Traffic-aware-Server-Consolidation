# Traffic-aware-Server-Consolidation
클라우드 환경에서 자원 및 네트워크 트래픽을 동시에 고려한 서버 통합 전략 연구

## 연구 배경 (Research Motivation)

현대 클라우드 데이터센터는 가상화(Virtualization) 기반으로 많은 VM을 운영하며,
전력·냉각·운영 비용 절감을 위해 **서버 통합(Server Consolidation)**을 적극 활용합니다.
하지만 기존 통합 기법들은 대부분 CPU·메모리 중심 자원 효율성에만 집중하거나 일부 연구는 **서비스 배치(placement)**만 다루고
실제로 서버를 해제하는 통합(consolidation) 단계는 고려하지 않으며 VM 간 네트워크 트래픽 비용을 반영하지 않는다는 한계점이 있습니다.
**트래픽을 고려한 서버 통합(traffic-aware consolidation)**은 대규모 클라우드 운영 비용 절감을 위해 매우 중요한 문제입니다.

## 제안 기법 개요
본 저장소에는 VM 간 트래픽 구조를 반영한 서버 통합 알고리즘의 실험 코드가 포함되어 있습니다.

### 후보 서버 선정
CPU/메모리 사용률, 자원 균형도, 인접 서버의 수용 가능 자원
→ 종합 점수를 기반으로 해제 가능성이 높은 서버를 선택

### VM 트래픽 그래프 구성
VM: Node
VM 간 트래픽량: Edge Weight

### 트래픽 기반 파티셔닝 (Super-node clustering)
상위 30% 강한 트래픽 엣지 기준으로 강하게 연결된 VM 그룹을 찾아 함께 마이그레이션
TREE / FAT-TREE / VL2 네트워크 구조별 비용 반영

## 실험 환경
### 서버 및 VM 구성

| 항목 | 값 |
|------|-----|
| 서버(Server) 개수 | 20, 40, 60 |
| VM 개수 | 60, 120, 180 |
| 서버 스펙 | RAM 64GB, MIPS 40,000 |
| 랙 구성 | 1 Rack = 4 Servers |
| VM 스펙 | 난수 기반 RAM/MIPS 생성 (고정 Seed 사용) |

### 트래픽 데이터
전체 edge 중 약 30%는 강한 트래픽(high-traffic)
* 일반 트래픽: 0 ~ 5
* 강한 트래픽: 50 ~ 100

### 네트워크 토폴로지
* TREE
* FAT-TREE
* VL2
각 구조마다 Rack 간 거리 비용 / multi-path 가능성이 다르게 적용

## 비교 알고리즘

FFD (First-Fit Decreasing)

Sercon (Balanced Resource Fit 기반 기존 연구)

Proposed (본 연구 Traffic-aware Consolidation)

## 실험 결과 요약
**📌 실험 1: 기존 기법 vs 제안 기법 (Server=20, VM=60)**

초기 실험에서는 트래픽 분포가 균일해 효과가 제한적이었음

Proposed → FFD 대비 1.79% 감소

**📌 실험 2: VM 수 증가 영향**

VM이 증가할수록 트래픽은 **선형이 아니라 초선형(Super-linear)**으로 증가
즉, 대규모 환경일수록 트래픽-aware 통합 필요성이 커짐

**📌 실험 3: 네트워크 토폴로지 영향**

VL2 구조에서 절감 효과 극대화

TREE 대비 트래픽 최대 74.86% 감소

FAT-TREE는 약 16% 감소

➡️ 네트워크 구조가 고도화될수록 제안 기법의 효과가 급증
