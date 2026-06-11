/**
 * google-analytics-dashboard.js
 * 통계 대시보드 차트 시각화 구현 비즈니스 로직
 */
document.addEventListener("DOMContentLoaded", function() {
    const cardId = currentCardId; // HTML(Thymeleaf)에서 정의된 변수 획득
    const rangeFilter = document.getElementById('rangeTypeFilter').value;
    const ctx = document.getElementById('analyticsChartCanvas').getContext('2d');

    let currentChart = null; // 중복 생성 방지용 차트 인스턴스 보관 변수

    // 1. 차트 데이터를 백엔드로부터 가져와서 차트를 그리는 함수
    function fetchAndRenderChart(rangeType) {
        // 백엔드 컨트롤러 매핑 주소 호출 (/card는 Context Path가 있을 시 브라우저가 자동 감지)
        fetch(`/card/api/google-analytics/stats?cardId=${currentCardId}&rangeType=${rangeType}`)
            .then(response => {
                if (!response.ok) {
                    throw new Error("통계 데이터를 가져오는 데 실패했습니다.");
                }
                return response.json();
            })
            .then(data => {
                // 백엔드가 준 Response 내부의 viewStats 배열 추출
                const stats = data.viewStats || [];

                // 구글이 제공하는 날짜 형식 "20260609"을 보기 좋은 "2026-06-09" 형식으로 가공 및 정렬
                stats.sort((a, b) => a.date.localeCompare(b.date));

                const labels = stats.map(item => formatGoogleDate(item.date));
                const viewCounts = stats.map(item => item.viewCount);

                // 차트 그리기 가동
                renderChart(labels, viewCounts);
            })
            .catch(error => {
                console.error("📊 GA 데이터 로드 에러:", error);
                alert("일시적인 오류로 통계 데이터를 불러오지 못했습니다.\n잠시 후 다시 시도해 주세요.");
            });
    }

    // 2. Chart.js 인스턴스 생성 및 갱신 함수
    function renderChart(labels, values) {
        // 기존에 그려진 차트가 있다면 파괴하고 새로 그려야 잔상이 생기지 않습니다.
        if (currentChart) {
            currentChart.destroy();
        }

        // 대시보드 디자인에 맞는 모던한 라인 차트 구성
        currentChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [{
                    label: '명함 페이지 조회수 (Views)',
                    data: values,
                    borderColor: '#4285F4', // 구글 브랜드 블루 색상
                    backgroundColor: 'rgba(66, 133, 244, 0.1)',
                    borderWidth: 3,
                    pointBackgroundColor: '#4285F4',
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    fill: true,
                    tension: 0.3 // 부드러운 곡선 효과
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: true, position: 'top' }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        ticks: {
                            callback: function(value) {
                                if (value % 1 === 0) return value; // 정수만 표시
                            }
                        }
                    }
                }
            }
        });
    }

    // "20260609" -> "26-06-09" 또는 "06/09" 형태로 가공하는 보조 함수
    function formatGoogleDate(dateStr) {
        if (dateStr && dateStr.length === 8) {
            return dateStr.substring(4, 6) + "/" + dateStr.substring(6, 8);
        }
        return dateStr;
    }

    // 3. 이벤트 리스너: 필터 변경 시 차트 동적 업데이트
    rangeFilter.addEventListener('change', function() {
        fetchAndRenderChart(this.value);
    });

    // 4. 최초 페이지 진입 시 기본값(7days)으로 차트 초기 로드
    fetchAndRenderChart('7days');
});