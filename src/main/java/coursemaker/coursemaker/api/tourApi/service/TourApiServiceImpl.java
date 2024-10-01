package coursemaker.coursemaker.api.tourApi.service;

import coursemaker.coursemaker.api.busanApi.dto.BusanApiResponse;
import coursemaker.coursemaker.api.busanApi.service.BusanApiService;
import coursemaker.coursemaker.api.tourApi.dto.TourApiResponse;
import coursemaker.coursemaker.api.tourApi.entity.TourApi;
import coursemaker.coursemaker.api.tourApi.repository.TourApiRepository;
import coursemaker.coursemaker.domain.destination.dto.LocationDto;
import coursemaker.coursemaker.domain.destination.dto.RequestDto;
import coursemaker.coursemaker.domain.destination.entity.Destination;
import coursemaker.coursemaker.domain.destination.repository.DestinationRepository;
import coursemaker.coursemaker.domain.destination.service.DestinationService;
import coursemaker.coursemaker.domain.member.entity.Member;
import coursemaker.coursemaker.domain.member.repository.MemberRepository;
import coursemaker.coursemaker.domain.tag.dto.TagResponseDto;
import coursemaker.coursemaker.domain.tag.exception.TagNotFoundException;
import coursemaker.coursemaker.domain.tag.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.yaml.snakeyaml.util.UriEncoder;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TourApiServiceImpl implements TourApiService {
    @Value("${tourapi.serviceKey}")
    private String serviceKey;

    @Value("${tourapi.baseUrl}")
    private String baseUrl;

    @Value("${tourapi.detailCommonUrl}")
    private String detailCommonUrl;

    @Value("${tourapi.disableTourUrl}")
    private String disableTourUrl;

    @Value("${tourapi.disableUpdateUrl}")
    private String disableUpdateUrl;

//    @Value("${tourapi.withPetUrl}")
//    private String withPetUrl;

    private final MemberRepository memberRepository;



    private final WebClient.Builder webClientBuilder;
    private final TourApiRepository tourApiRepository;
    private final DestinationRepository destinationRepository;
    private final BusanApiService busanApiService;
    private final DestinationService destinationService;
    private final TagService tagService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);


    @Override
    public TourApiResponse updateAndGetTour() {
        log.info("[TourApi] 투어 API 업데이트 시작");
        CompletableFuture<TourApiResponse> initialUpdateFuture = CompletableFuture.supplyAsync(this::initialUpdate, executorService);

        CompletableFuture<BusanApiResponse> busanApiUpdateFuture = CompletableFuture.supplyAsync(busanApiService::initialUpdate, executorService);

        busanApiUpdateFuture
                .thenRunAsync(busanApiService::busanConvertAndSaveToDestination, executorService)
                .join();

        initialUpdateFuture
                .thenComposeAsync(response -> updateDisabledTours()
                        .thenRunAsync(this::handleMissingDisabledData, executorService)
                        .thenComposeAsync(aVoid -> {
                            log.info("[TourApi] 상세 데이터 업데이트 시작");
                            List<CompletableFuture<Void>> updateFutures = tourApiRepository.findAll().stream()
                                    .map(tour -> CompletableFuture.runAsync(() -> updateCommonData(tour.getId()), executorService))
                                    .collect(Collectors.toList());
                            return CompletableFuture.allOf(updateFutures.toArray(new CompletableFuture[0]));
                        }, executorService), executorService)
                .thenRunAsync(this::updateMissingData, executorService)
                .thenRunAsync(this::convertAndSaveToDestination, executorService)
                .join();

        log.info("[TourApi] 투어 API 업데이트 완료");
        return initialUpdateFuture.join();
    }


//    @Override
//    public TourApiResponse updateAndGetTour() {
//        // Step 1: 국문 관광 API 데이터를 초기화하고 업데이트
//        CompletableFuture<TourApiResponse> initialUpdateFuture = CompletableFuture.supplyAsync(this::initialUpdate, executorService);
//
//        // Step 2: 부산 API 데이터를 업데이트
//        CompletableFuture<BusanApiResponse> busanApiUpdateFuture = CompletableFuture.supplyAsync(busanApiService::initialUpdate, executorService);
//
//        // Step 3: 초기화된 데이터를 기반으로 비활성화된 투어 업데이트 및 누락된 비활성화 데이터 처리
//        initialUpdateFuture
//                .thenComposeAsync(response -> updateDisabledTours()
//                        .thenRunAsync(this::handleMissingDisabledData, executorService)
//                        .thenComposeAsync(aVoid -> {
//                            // 필터링된 데이터를 처리하기 위해 필터링된 TourApi 리스트 생성
//                            List<TourApi> filteredTours = tourApiRepository.findAll().stream()
//                                    .filter(tour -> !isExcludedCat1(tour.getCat1()))
//                                    .filter(tour -> !isExcludedCat3(tour.getCat3()))
//                                    .collect(Collectors.toList());
//
//                            // Step 4: 필터링된 투어 데이터에서 반려동물 여행지 여부를 확인하고 업데이트
//                            return updateWithPetTours(filteredTours)
//                                    .thenComposeAsync(v -> {
//                                        // Step 5: 필터링된 투어 데이터를 기반으로 기본 데이터를 업데이트
//                                        List<CompletableFuture<Void>> updateFutures = filteredTours.stream()
//                                                .map(tour -> CompletableFuture.runAsync(() -> updateCommonData(tour.getId()), executorService))
//                                                .collect(Collectors.toList());
//                                        return CompletableFuture.allOf(updateFutures.toArray(new CompletableFuture[0]));
//                                    });
//                        }, executorService), executorService)
//                .thenRunAsync(this::updateMissingData, executorService) // Step 6: 누락된 데이터를 처리
//                .thenRunAsync(this::convertAndSaveToDestination, executorService) // Step 7: 데이터를 Destination 엔티티로 변환 및 저장
//                .join(); // 모든 작업이 완료될 때까지 대기
//
//        // Step 8: 부산 API 데이터를 Destination 엔티티로 변환 및 저장
//        busanApiUpdateFuture
//                .thenRunAsync(busanApiService::busanConvertAndSaveToDestination, executorService)
//                .join(); // 모든 작업이 완료될 때까지 대기
//
//        // Step 9: 반려동물 여행지 데이터에서 누락된 데이터를 확인하고 다시 요청하여 처리
//        CompletableFuture.runAsync(this::updateMissingPetFriendlyTours, executorService)
//                .join(); // 모든 작업이 완료될 때까지 대기
//
//        // Step 10: 초기 업데이트 결과 반환
//        return initialUpdateFuture.join();
//    }

    private TourApiResponse initialUpdate() {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("numOfRows", 2300)
                .queryParam("pageNo", 1)
                .queryParam("MobileOS", "WIN")
                .queryParam("MobileApp", UriEncoder.encode("코스메이커"))
                .queryParam("_type", "json")
                .queryParam("areaCode", 6)
                .queryParam("serviceKey", serviceKey)
                .build(true)
                .toUri();

        log.debug("[TourApi] 투어 API 초기 데이터 요청: {}", uri);

        try {
            TourApiResponse response = webClientBuilder.build().get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(status -> status.value() >= 400, clientResponse -> {
                        log.error("[TourApi] API 오류 응답 코드: {}", clientResponse.statusCode().value());
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("[TourApi] API 오류 응답 본문: {}", errorBody);
                                    return Mono.error(new RuntimeException("API 오류 응답: " + clientResponse.statusCode().value() + " " + errorBody));
                                });
                    })
                    .bodyToMono(TourApiResponse.class)
                    .block();

            if (response != null && response.getResponse().getBody().getItems().getItem() != null) {
                log.info("[TourApi] 투어 API 초기 데이터 응답 수신: {} 개 항목", response.getResponse().getBody().getItems().getItem().size());
                List<TourApi> tourList = response.getResponse().getBody().getItems().getItem().stream()
                        .filter(item -> !isExcludedCat1(item.getCat1())) // cat1 필터링
                        .filter(item -> !isExcludedCat3(item.getCat3())) // cat3 필터링
                        .filter(item -> !isExcludedDestination(item.getContentid()))
                        .map(this::convertToEntity)
                        .collect(Collectors.toList());
                synchronized (this) {
                    tourList.forEach(this::saveOrUpdateTour);
                }
            }
            return response;
        } catch (Exception e) {
            log.error("[TourApi] 투어 업데이트 중 예외 발생: ", e);
            throw new RuntimeException("투어 업데이트 실패", e);
        }
    }

    private  boolean isExcludedDestination(long contentId) {
        List<Long> contentIdList = List.of(2933698L, 1104412L);
        return contentIdList.contains(contentId);
    }

    private boolean isExcludedCat1(String cat1) {
        List<String> excludedCat1List = List.of(
                "B02"
        );
        return excludedCat1List.contains(cat1);
    }

    private boolean isExcludedCat3(String cat3) {
        List<String> excludedCat3List = List.of(
                "A02020600", "A02020700", "A02040400", "A02040600", "A02040800", "A02040900",
                "A02041000", "A02050100", "A02050300", "A02050400", "A02050500", "A02050600", "A02060900",
                "A02061000", "A02061200", "A02061300", "A02061400", "A02080200", "A02080300", "A02080400",
                "A02080500", "A02080600", "A02080800", "A02080900", "A02081000", "A02081100", "A02081300",
                "C01120001", "C01150001", "C01170001", "A04010200", "A04010300", "A04010400", "A04010500",
                "A04010600", "A04010700", "A04011000"

        );
        return excludedCat3List.contains(cat3);
    }

    private CompletableFuture<Void> updateDisabledTours() {
        log.info("[TourApi] 무장애 투어 정보 업데이트 시작");
        return CompletableFuture.runAsync(() -> {
            URI uri = UriComponentsBuilder.fromHttpUrl(disableTourUrl)
                    .queryParam("numOfRows", 2300)
                    .queryParam("pageNo", 1)
                    .queryParam("MobileOS", "WIN")
                    .queryParam("MobileApp", UriEncoder.encode("코스메이커"))
                    .queryParam("_type", "json")
                    .queryParam("areaCode", 6)
                    .queryParam("serviceKey", serviceKey)
                    .build(true)
                    .toUri();

            log.debug("[TourApi] 무장애 투어 정보 데이터 요청: {}", uri);

            try {
                TourApiResponse response = webClientBuilder.build().get()
                        .uri(uri)
                        .retrieve()
                        .bodyToMono(TourApiResponse.class)
                        .timeout(Duration.ofSeconds(10))
                        .retryWhen(Retry.backoff(3, Duration.ofSeconds(10)))
                        .block();

                if (response != null && response.getResponse().getBody().getItems().getItem() != null) {
                    log.info("[TourApi] 무장애 투어 정보 데이터 수신: {} 개 항목", response.getResponse().getBody().getItems().getItem().size());
                    List<Long> disabledContentIds = response.getResponse().getBody().getItems().getItem().stream()
                            .map(TourApiResponse.Item::getContentid)
                            .collect(Collectors.toList());

                    List<TourApi> allTours = tourApiRepository.findAll();
                    allTours.forEach(tour -> {
                        if (disabledContentIds.contains(tour.getContentid())) {
                            tour.setDisabled(true);
                        } else {
                            tour.setDisabled(false);
                        }
                        tourApiRepository.save(tour);
                    });
                }
            } catch (Exception e) {
                log.error("[TourApi] 무장애 투어 정보 업데이트 중 오류 발생: ", e);
            }
        }, executorService);
    }

    private void handleMissingDisabledData() {
        log.info("[TourApi] 누락된 무장애 투어 정보 데이터 처리 시작");
        List<TourApi> allTours = tourApiRepository.findAll();
        for (TourApi tour : allTours) {
            if (tour.getDisabled() == null) {
                log.debug("[TourApi] 누락된 누락된 무장애 투어 데이터 재시도: contentId={}", tour.getContentid());
                retryUpdateDisabledData(tour.getContentid());
            }
        }
    }

    private void retryUpdateDisabledData(long contentId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(disableUpdateUrl)
                .queryParam("numOfRows", 1)
                .queryParam("pageNo", 1)
                .queryParam("MobileOS", "WIN")
                .queryParam("MobileApp", UriEncoder.encode("코스메이커"))
                .queryParam("_type", "json")
                .queryParam("serviceKey", serviceKey)
                .queryParam("contentId", contentId)
                .build(true)
                .toUri();

        try {
            TourApiResponse response = webClientBuilder.build().get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(TourApiResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(10)))
                    .block();

            if (response != null && response.getResponse().getBody().getItems().getItem() != null) {
                log.debug("[TourApi] 무장애 투어 데이터 재시도 응답 수신: contentId={}", contentId);
                response.getResponse().getBody().getItems().getItem().forEach(item -> {
                    Optional<TourApi> tourApiOptional = tourApiRepository.findByContentid(item.getContentid());
                    tourApiOptional.ifPresent(tourApi -> {
                        synchronized (this) {
                            tourApi.setDisabled(true);
                            tourApiRepository.save(tourApi);
                        }
                    });
                });
            } else {
                log.error("[TourApi] 유효하지 않은 응답: contentId={}", contentId);
            }
        } catch (Exception e) {
            log.error("[TourApi] 무장애 투어 데이터 재시도 중 예외 발생: contentId={}", contentId, e);
        }
    }

    private void updateCommonData(long id) {
        Optional<TourApi> getTourApi = tourApiRepository.findById(id);
        if (getTourApi.isPresent()) {
            long contentId = getTourApi.get().getContentid();
            int pageNo = 1;
            boolean moreData = true;

            log.info("[TourApi] 상세 데이터 업데이트 시작: contentId={}", contentId);

            while (moreData) {
                URI uri = UriComponentsBuilder.fromHttpUrl(detailCommonUrl)
                        .queryParam("numOfRows", 100) // 한 페이지에 가져올 데이터 수
                        .queryParam("pageNo", pageNo)
                        .queryParam("MobileOS", "WIN")
                        .queryParam("MobileApp", UriEncoder.encode("코스메이커"))
                        .queryParam("_type", "json")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("defaultYN", "Y")
                        .queryParam("overviewYN", "Y")
                        .queryParam("contentId", contentId)
                        .build(true)
                        .toUri();

                try {
                    TourApiResponse response = webClientBuilder.build().get()
                            .uri(uri)
                            .retrieve()
                            .onStatus(status -> status.value() >= 400, clientResponse -> {
                                log.error("[TourApi] API 오류 응답 코드: {}", clientResponse.statusCode().value());
                                return clientResponse.bodyToMono(String.class)
                                        .flatMap(errorBody -> Mono.error(new RuntimeException("API 오류 응답: " + clientResponse.statusCode().value() + " " + errorBody)));
                            })
                            .bodyToMono(TourApiResponse.class)
                            .timeout(Duration.ofSeconds(10))
                            .retryWhen(Retry.backoff(3, Duration.ofSeconds(10)))
                            .block();

                    if (response != null && response.getResponse().getBody().getItems().getItem() != null) {
                        log.debug("[TourApi] 상세 데이터 응답 수신: contentId={}", contentId);
                        List<TourApiResponse.Item> items = response.getResponse().getBody().getItems().getItem();
                        items.forEach(item -> {
                            Optional<TourApi> tourApiOptional = tourApiRepository.findByContentid(item.getContentid());
                            tourApiOptional.ifPresent(tourApi -> {
                                synchronized (this) {
                                    tourApi.setHomepage(item.getHomepage());
                                    tourApi.setOverview(item.getOverview());
                                    tourApiRepository.save(tourApi);
                                }
                            });
                        });

                        if (items.size() < 100) {
                            moreData = false; // 더 이상 데이터가 없으면 루프 종료
                        } else {
                            pageNo++; // 다음 페이지로 이동
                        }
                    } else {
                        moreData = false; // 응답이 없거나 유효하지 않으면 루프 종료
                    }
                } catch (Exception e) {
                    log.error("[TourApi] 상세 데이터 업데이트 중 오류 발생: contentId={}", contentId, e);
                    moreData = false; // 예외 발생 시 루프 종료
                }
            }
        }
    }

    private void updateMissingData() {
        log.info("[TourApi] 누락된 상세 데이터 업데이트 시작");
        List<TourApi> missingDataTours = tourApiRepository.findAll().stream()
                .filter(tour -> tour.getOverview() == null)
                .collect(Collectors.toList());

        for (TourApi tour : missingDataTours) {
            log.debug("[TourApi] 누락된 상세 데이터 재시도: contentId={}", tour.getContentid());
            retryUpdateCommonData(tour.getContentid());
        }
    }

    private void retryUpdateCommonData(long contentId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(detailCommonUrl)
                .queryParam("numOfRows", 1)
                .queryParam("pageNo", 1)
                .queryParam("MobileOS", "WIN")
                .queryParam("MobileApp", UriEncoder.encode("코스메이커"))
                .queryParam("_type", "json")
                .queryParam("serviceKey", serviceKey)
                .queryParam("defaultYN", "Y")
                .queryParam("overviewYN", "Y")
                .queryParam("contentId", contentId)
                .build(true)
                .toUri();

        try {
            TourApiResponse response = webClientBuilder.build().get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(TourApiResponse.class)
                    .timeout(Duration.ofSeconds(10)) // 타임아웃 설정
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(10)))
                    .block();

            if (response != null && response.getResponse().getBody().getItems().getItem() != null) {
                log.debug("[TourApi] 상세 데이터 재시도 응답 수신: contentId={}", contentId);
                response.getResponse().getBody().getItems().getItem().forEach(item -> {
                    Optional<TourApi> tourApiOptional = tourApiRepository.findByContentid(item.getContentid());
                    tourApiOptional.ifPresent(tourApi -> {
                        synchronized (this) {
                            tourApi.setHomepage(item.getHomepage());
                            tourApi.setOverview(item.getOverview());
                            tourApiRepository.save(tourApi);
                        }
                    });
                });
            } else {
                log.error("[TourApi] 유효하지 않은 응답: contentId={}", contentId);
            }
        } catch (Exception e) {
            log.error("[TourApi] 상세 데이터 재시도 중 예외 발생: contentId={}", contentId, e);
        }
    }

//    private CompletableFuture<Void> updateWithPetTours(List<TourApi> filteredTours) {
//        URI uri = UriComponentsBuilder.fromHttpUrl(withPetUrl)
//                .queryParam("numOfRows", 100) // 한 페이지에 가져올 데이터 수
//                .queryParam("pageNo", 1)
//                .queryParam("MobileOS", "WIN")
//                .queryParam("MobileApp", UriEncoder.encode("코스메이커"))
//                .queryParam("_type", "json")
//                .queryParam("serviceKey", serviceKey)
//                .build(true)
//                .toUri();
//
//        return CompletableFuture.runAsync(() -> {
//            try {
//                TourApiResponse response = webClientBuilder.build().get()
//                        .uri(uri)
//                        .retrieve()
//                        .bodyToMono(TourApiResponse.class)
//                        .block();
//
//                if (response != null && response.getResponse().getBody().getItems().getItem() != null) {
//                    List<Long> petFriendlyContentIds = response.getResponse().getBody().getItems().getItem().stream()
//                            .map(TourApiResponse.Item::getContentid)
//                            .collect(Collectors.toList());
//
//                    filteredTours.forEach(tour -> {
//                        if (petFriendlyContentIds.contains(tour.getContentid())) {
//                            tour.setWithPet(1);  // 반려동물 여행지 표시
//                        } else {
//                            tour.setWithPet(0);  // 반려동물 여행지가 아니면 0으로 설정
//                        }
//                        tourApiRepository.save(tour);
//                    });
//                }
//            } catch (Exception e) {
//                log.error("Error fetching with pet info: ", e);
//            }
//        }, executorService);
//    }
//
//
//    private void updateMissingPetFriendlyTours() {
//        // withPet 값이 null인 데이터를 필터링하여 처리
//        List<TourApi> missingPetFriendlyTours = tourApiRepository.findAll().stream()
//                .filter(tour -> tour.getWithPet() == null) // withPet 값이 null인 데이터만 필터링
//                .collect(Collectors.toList());
//
//        // 누락된 데이터를 대상으로 재요청
//        for (TourApi tour : missingPetFriendlyTours) {
//            retryUpdateWithPet(tour.getContentid());
//        }
//    }
//
//
//    private void retryUpdateWithPet(long contentId) {
//        URI uri = UriComponentsBuilder.fromHttpUrl(withPetUrl)
//                .queryParam("numOfRows", 1)
//                .queryParam("pageNo", 1)
//                .queryParam("MobileOS", "WIN")
//                .queryParam("MobileApp", UriEncoder.encode("코스메이커"))
//                .queryParam("_type", "json")
//                .queryParam("serviceKey", serviceKey)
//                .queryParam("contentId", contentId) // contentId로 다시 조회
//                .build(true)
//                .toUri();
//
//        try {
//            TourApiResponse response = webClientBuilder.build().get()
//                    .uri(uri)
//                    .retrieve()
//                    .bodyToMono(TourApiResponse.class)
//                    .timeout(Duration.ofSeconds(10)) // 타임아웃 설정
//                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(10))) // 재시도 로직 추가
//                    .block();
//
//            if (response != null && response.getResponse().getBody().getItems().getItem() != null) {
//                response.getResponse().getBody().getItems().getItem().forEach(item -> {
//                    Optional<TourApi> tourApiOptional = tourApiRepository.findByContentid(item.getContentid());
//                    tourApiOptional.ifPresent(tourApi -> {
//                        synchronized (this) {
//                            tourApi.setWithPet(1); // 반려동물 여행지로 확인되면 withPet 값을 1로 설정
//                            tourApiRepository.save(tourApi);
//                        }
//                    });
//                });
//            } else {
//                log.error("Invalid response for contentId: {}", contentId);
//            }
//        } catch (Exception e) {
//            log.error("Exception occurred while retrying pet-friendly data for contentId: {}", contentId, e);
//        }
//    }

    @Override
    public List<TourApi> getAllTours() {
        log.debug("[TourApi] 모든 투어 데이터 조회 요청");
        return tourApiRepository.findAll();
    }

    @Override
    public Optional<TourApi> getTourById(Long id) {
        log.debug("[TourApi] ID로 투어 데이터 조회 요청: ID={}", id);
        return tourApiRepository.findById(id);
    }

    private void convertAndSaveToDestination() {
        log.info("[TourApi] 투어 데이터를 Destination으로 변환 및 저장 시작");
        List<TourApi> tourApis = tourApiRepository.findAll();
        for (TourApi tourApi : tourApis) {
            // title의 길이가 30자 초과인 경우 저장하지 않음
            if (tourApi.getTitle().length() > 30) {
                log.debug("[TourApi] 제목이 30자를 초과하여 저장하지 않음: title={}", tourApi.getTitle());
                continue;
            }

            // Destination 테이블에 이미 해당 TourApi의 contentid가 있는지 확인
            Optional<Destination> existingDestination = destinationRepository.findByContentIdAndDeletedAtIsNull(tourApi.getContentid());
            if (existingDestination.isEmpty()) {
                log.info("[TourApi] 새로운 Destination 저장: contentId={}", tourApi.getContentid());
                // Destination 테이블에 해당 항목이 없으면 새로 저장
                RequestDto dto = new RequestDto();
                LocationDto locationDto = new LocationDto(tourApi.getAddr1(), tourApi.getMapx(), tourApi.getMapy());
                Optional<Member> adminMember = memberRepository.findById(1L);
                List<TagResponseDto> tags = tagService.findAllTags();
                // 자연
                TagResponseDto mountainTag = tags.stream().filter(tag -> "산".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "산 태그"));
                TagResponseDto seaTag = tags.stream().filter(tag -> "바다".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "바다 태그"));
                TagResponseDto natureViewTag = tags.stream().filter(tag -> "자연경관".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "자연경관 태그"));
                TagResponseDto nationalParkTag = tags.stream().filter(tag -> "국/공립공원".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "국/공립공원 태그"));
                TagResponseDto trailTag = tags.stream().filter(tag -> "둘레길".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "둘레길 태그"));

                // 동행
                TagResponseDto soloTravelTag = tags.stream().filter(tag -> "나홀로 여행".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "나홀로 여행 태그"));
                TagResponseDto withKidsTag = tags.stream().filter(tag -> "유아동반".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "유아동반 태그"));
                TagResponseDto accessibleTravelTag = tags.stream().filter(tag -> "무장애여행".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "무장애여행 태그"));
                TagResponseDto childrenTravelTag = tags.stream().filter(tag -> "어린이여행".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "어린이여행 태그"));
                TagResponseDto parentsTravelTag = tags.stream().filter(tag -> "부모님".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "부모님 태그"));
                TagResponseDto coupleTravelTag = tags.stream().filter(tag -> "연인/배우자".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "연인/배우자 태그"));
                TagResponseDto friendsTravelTag = tags.stream().filter(tag -> "친구".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "친구 태그"));
                TagResponseDto withPetsTag = tags.stream().filter(tag -> "애견동반".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "애견동반 태그"));


                // 활동
                TagResponseDto activityTag = tags.stream().filter(tag -> "액티비티".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "액티비티 태그"));
                TagResponseDto cultureMuseumTag = tags.stream().filter(tag -> "문화/박물관".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "문화/박물관 태그"));
                TagResponseDto festivalPerformanceTag = tags.stream().filter(tag -> "축제/공연".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "축제/공연 태그"));
                TagResponseDto experienceTravelTag = tags.stream().filter(tag -> "체험여행".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "체험여행 태그"));
                TagResponseDto traditionalMarketTag = tags.stream().filter(tag -> "전통시장".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "전통시장 태그"));
                TagResponseDto historicalSiteTag = tags.stream().filter(tag -> "역사/유적지".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "역사/유적지 태그"));
                TagResponseDto restFacilityTag = tags.stream().filter(tag -> "휴식시설".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "휴식시설 태그"));
                TagResponseDto foodTravelTag = tags.stream().filter(tag -> "식도락".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "식도락 태그"));

                // 날씨
                TagResponseDto operatingInRainTag = tags.stream().filter(tag -> "우천시 운영".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "우천시 운영 태그"));
                TagResponseDto indoorSpaceTag = tags.stream().filter(tag -> "실내공간".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "실내공간 태그"));
                TagResponseDto vacationSpotTag = tags.stream().filter(tag -> "피서지".equals(tag.getName())).findFirst()
                        .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "피서지 태그"));

                // 산 + (무장애)
                if (tourApi.getCat3().equals("A01010400")) {
                    if (tourApi.getDisabled()) {
                        dto.setTags(List.of(mountainTag, accessibleTravelTag));
                    } else {
                        dto.setTags(List.of(mountainTag));
                    }
                    // 바다 + (무장애)
                } else if (tourApi.getCat3().equals("A01011100") || tourApi.getCat3().equals("A01011200") || tourApi.getCat3().equals("A01011300") || tourApi.getCat3().equals("A01011400") || tourApi.getCat3().equals("A01011600")) {
                    if (tourApi.getDisabled()) {
                        dto.setTags(List.of(seaTag, accessibleTravelTag));
                    } else {
                        dto.setTags(List.of(seaTag));
                    }
                    // 자연경관 + (무장애)
                } else if (tourApi.getCat3().equals("A01010500") || tourApi.getCat3().equals("A01010600") || tourApi.getCat3().equals("A01010700") || tourApi.getCat3().equals("A01010800") || tourApi.getCat3().equals("A01010900") || tourApi.getCat3().equals("A01011000") || tourApi.getCat3().equals("A01011700") || tourApi.getCat3().equals("A01011800") || tourApi.getCat3().equals("A01011900") || tourApi.getCat3().equals("A01020100") || tourApi.getCat3().equals("A01020200")) {
                    if (tourApi.getDisabled()) {
                        dto.setTags(List.of(natureViewTag, accessibleTravelTag));
                    } else {
                        dto.setTags(List.of(natureViewTag));
                    }
                    // 국/공립공원 + (무장애)
                } else if (tourApi.getCat3().equals("A01010100") || tourApi.getCat3().equals("A01010200") || tourApi.getCat3().equals("A01010300")) {
                    if (tourApi.getDisabled()) {
                        dto.setTags(List.of(nationalParkTag, accessibleTravelTag));
                    } else {
                        dto.setTags(List.of(nationalParkTag));
                    }
                    // 나홀로 여행 + (무장애)
                } else if (tourApi.getCat3().equals("C01130001")) {
                    if (tourApi.getDisabled()) {
                        dto.setTags(List.of(soloTravelTag, accessibleTravelTag));
                    } else {
                        dto.setTags(List.of(soloTravelTag));
                    }
                    // 부모님 + (무장애)
                } else if (tourApi.getCat3().equals("C01140001")) {
                    if (tourApi.getDisabled()) {
                        dto.setTags(List.of(parentsTravelTag, accessibleTravelTag));
                    } else {
                        dto.setTags(List.of(parentsTravelTag));
                    }
                    // 액티비티 + (무장애)
                } else if (tourApi.getCat2().equals("A0301") || tourApi.getCat2().equals("A0302") || tourApi.getCat2().equals("A0303") || tourApi.getCat2().equals("A0304") || tourApi.getCat2().equals("A0305") || tourApi.getCat3().equals("C01160001")) {
                    if (tourApi.getDisabled()) {
                        dto.setTags(List.of(activityTag, accessibleTravelTag));
                    } else {
                        dto.setTags(List.of(activityTag));
                    }
                    // 문화/박물관 + (무장애)
                } else if (tourApi.getCat3().equals("A02060100") || tourApi.getCat3().equals("A02060200") || tourApi.getCat3().equals("A02060300") || tourApi.getCat3().equals("A02060400") || tourApi.getCat3().equals("A02060500") || tourApi.getCat3().equals("A02060600") || tourApi.getCat3().equals("A02060700") || tourApi.getCat3().equals("A02060800") || tourApi.getCat3().equals("A02061100")) {
                    if (tourApi.getDisabled()) {
                        dto.setTags(List.of(cultureMuseumTag, accessibleTravelTag));
                    } else {
                        dto.setTags(List.of(cultureMuseumTag));
                    }
                    // 축제/공연 + (무장애)
                } else if (tourApi.getCat3().equals("A02070100") || tourApi.getCat3().equals("A02070200") || tourApi.getCat3().equals("A02080100") || tourApi.getCat3().equals("A02081200")) {
                    if (tourApi.getDisabled()) {
                        dto.setTags(List.of(festivalPerformanceTag, accessibleTravelTag));
                    } else {
                        dto.setTags(List.of(festivalPerformanceTag));
                    }
                    // 체험여행 + (무장애)
                } else if (tourApi.getCat3().equals("A02020800") || tourApi.getCat3().equals("A02030100") || tourApi.getCat3().equals("A02030200") || tourApi.getCat3().equals("A02030300") || tourApi.getCat3().equals("A02030400") || tourApi.getCat3().equals("A02030600")) {
                    if (tourApi.getDisabled()) {
                        dto.setTags(List.of(experienceTravelTag, accessibleTravelTag));
                    } else {
                        dto.setTags(List.of(experienceTravelTag));
                    }
                    // 전통시장 + (무장애)
                } else if (tourApi.getCat3().equals("A04010100") || tourApi.getCat3().equals("A04010900")) {
                    if (tourApi.getDisabled()) {
                        dto.setTags(List.of(traditionalMarketTag, accessibleTravelTag));
                    } else {
                        dto.setTags(List.of(traditionalMarketTag));
                    }
                    // 역사/유적지 + (무장애)
                } else if (tourApi.getCat2().equals("A0201") || tourApi.getCat3().equals("A02020200") || tourApi.getCat3().equals("A02050200")) {
                    if (tourApi.getDisabled()) {
                        dto.setTags(List.of(historicalSiteTag, accessibleTravelTag));
                    } else {
                        dto.setTags(List.of(historicalSiteTag));
                    }
                    // 휴식시설 + (무장애)
                } else if (tourApi.getCat3().equals("A02020300") || tourApi.getCat3().equals("A02020400") || tourApi.getCat3().equals("A02020500")) {
                    if (tourApi.getDisabled()) {
                        dto.setTags(List.of(restFacilityTag, accessibleTravelTag));
                    } else {
                        dto.setTags(List.of(restFacilityTag));
                    }
                    // 식도락 + (무장애)
                } else if (tourApi.getCat2().equals("A0502")) {
                    if (tourApi.getDisabled()) {
                        dto.setTags(List.of(foodTravelTag, accessibleTravelTag));
                    } else {
                        dto.setTags(List.of(foodTravelTag));
                    }
                }
//                dto.setTags(List.of(natureViewTag));
                dto.setName(tourApi.getTitle());
                dto.setPictureLink(tourApi.getFirstimage());
//                dto.setContent(tourApi.getOverview());
                dto.setApiContent(tourApi.getOverview());
                dto.setLocation(locationDto);
                dto.setDisabled(tourApi.getDisabled());
//                dto.setWithPet(tourApi.getWithPet());
                dto.setContentId(tourApi.getContentid());
                dto.setIsApiData(true);
                dto.setAverageRating(0d);
                dto.setNickname(adminMember.get().getNickname());

                destinationService.save(dto);
            } else if (existingDestination.isPresent()) {
                Destination existing = existingDestination.get();
                if (!existing.equals(tourApi)) {
                    log.info("[TourApi] 존재하는 Destination 변경사항 있으면 업데이트: contentId={}", tourApi.getContentid());
                    Long destinationId = existing.getId();
                    RequestDto dto = new RequestDto();
                    LocationDto locationDto = new LocationDto(tourApi.getAddr1(), tourApi.getMapx(), tourApi.getMapy());
                    Optional<Member> adminMember = memberRepository.findById(1L);
                    List<TagResponseDto> tags = tagService.findAllTags();
                    // 자연
                    TagResponseDto mountainTag = tags.stream().filter(tag -> "산".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "산 태그"));
                    TagResponseDto seaTag = tags.stream().filter(tag -> "바다".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "바다 태그"));
                    TagResponseDto natureViewTag = tags.stream().filter(tag -> "자연경관".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "자연경관 태그"));
                    TagResponseDto nationalParkTag = tags.stream().filter(tag -> "국/공립공원".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "국/공립공원 태그"));
                    TagResponseDto trailTag = tags.stream().filter(tag -> "둘레길".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "둘레길 태그"));

                    // 동행
                    TagResponseDto soloTravelTag = tags.stream().filter(tag -> "나홀로 여행".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "나홀로 여행 태그"));
                    TagResponseDto withKidsTag = tags.stream().filter(tag -> "유아동반".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "유아동반 태그"));
                    TagResponseDto accessibleTravelTag = tags.stream().filter(tag -> "무장애여행".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "무장애여행 태그"));
                    TagResponseDto childrenTravelTag = tags.stream().filter(tag -> "어린이여행".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "어린이여행 태그"));
                    TagResponseDto parentsTravelTag = tags.stream().filter(tag -> "부모님".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "부모님 태그"));
                    TagResponseDto coupleTravelTag = tags.stream().filter(tag -> "연인/배우자".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "연인/배우자 태그"));
                    TagResponseDto friendsTravelTag = tags.stream().filter(tag -> "친구".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "친구 태그"));
                    TagResponseDto withPetsTag = tags.stream().filter(tag -> "애견동반".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "애견동반 태그"));


                    // 활동
                    TagResponseDto activityTag = tags.stream().filter(tag -> "액티비티".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "액티비티 태그"));
                    TagResponseDto cultureMuseumTag = tags.stream().filter(tag -> "문화/박물관".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "문화/박물관 태그"));
                    TagResponseDto festivalPerformanceTag = tags.stream().filter(tag -> "축제/공연".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "축제/공연 태그"));
                    TagResponseDto experienceTravelTag = tags.stream().filter(tag -> "체험여행".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "체험여행 태그"));
                    TagResponseDto traditionalMarketTag = tags.stream().filter(tag -> "전통시장".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "전통시장 태그"));
                    TagResponseDto historicalSiteTag = tags.stream().filter(tag -> "역사/유적지".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "역사/유적지 태그"));
                    TagResponseDto restFacilityTag = tags.stream().filter(tag -> "휴식시설".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "휴식시설 태그"));
                    TagResponseDto foodTravelTag = tags.stream().filter(tag -> "식도락".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "식도락 태그"));

                    // 날씨
                    TagResponseDto operatingInRainTag = tags.stream().filter(tag -> "우천시 운영".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "우천시 운영 태그"));
                    TagResponseDto indoorSpaceTag = tags.stream().filter(tag -> "실내공간".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "실내공간 태그"));
                    TagResponseDto vacationSpotTag = tags.stream().filter(tag -> "피서지".equals(tag.getName())).findFirst()
                            .orElseThrow(() -> new TagNotFoundException("해당 태그가 존재하지 않습니다.", "피서지 태그"));

                    // 산 + (무장애)
                    if (tourApi.getCat3().equals("A01010400")) {
                        if (tourApi.getDisabled()) {
                            dto.setTags(List.of(mountainTag, accessibleTravelTag));
                        } else {
                            dto.setTags(List.of(mountainTag));
                        }
                        // 바다 + (무장애)
                    } else if (tourApi.getCat3().equals("A01011100") || tourApi.getCat3().equals("A01011200") || tourApi.getCat3().equals("A01011300") || tourApi.getCat3().equals("A01011400") || tourApi.getCat3().equals("A01011600")) {
                        if (tourApi.getDisabled()) {
                            dto.setTags(List.of(seaTag, accessibleTravelTag));
                        } else {
                            dto.setTags(List.of(seaTag));
                        }
                        // 자연경관 + (무장애)
                    } else if (tourApi.getCat3().equals("A01010500") || tourApi.getCat3().equals("A01010600") || tourApi.getCat3().equals("A01010700") || tourApi.getCat3().equals("A01010800") || tourApi.getCat3().equals("A01010900") || tourApi.getCat3().equals("A01011000") || tourApi.getCat3().equals("A01011700") || tourApi.getCat3().equals("A01011800") || tourApi.getCat3().equals("A01011900") || tourApi.getCat3().equals("A01020100") || tourApi.getCat3().equals("A01020200")) {
                        if (tourApi.getDisabled()) {
                            dto.setTags(List.of(natureViewTag, accessibleTravelTag));
                        } else {
                            dto.setTags(List.of(natureViewTag));
                        }
                        // 국/공립공원 + (무장애)
                    } else if (tourApi.getCat3().equals("A01010100") || tourApi.getCat3().equals("A01010200") || tourApi.getCat3().equals("A01010300")) {
                        if (tourApi.getDisabled()) {
                            dto.setTags(List.of(nationalParkTag, accessibleTravelTag));
                        } else {
                            dto.setTags(List.of(nationalParkTag));
                        }
                        // 나홀로 여행 + (무장애)
                    } else if (tourApi.getCat3().equals("C01130001")) {
                        if (tourApi.getDisabled()) {
                            dto.setTags(List.of(soloTravelTag, accessibleTravelTag));
                        } else {
                            dto.setTags(List.of(soloTravelTag));
                        }
                        // 부모님 + (무장애)
                    } else if (tourApi.getCat3().equals("C01140001")) {
                        if (tourApi.getDisabled()) {
                            dto.setTags(List.of(parentsTravelTag, accessibleTravelTag));
                        } else {
                            dto.setTags(List.of(parentsTravelTag));
                        }
                        // 액티비티 + (무장애)
                    } else if (tourApi.getCat2().equals("A0301") || tourApi.getCat2().equals("A0302") || tourApi.getCat2().equals("A0303") || tourApi.getCat2().equals("A0304") || tourApi.getCat2().equals("A0305") || tourApi.getCat3().equals("C01160001")) {
                        if (tourApi.getDisabled()) {
                            dto.setTags(List.of(activityTag, accessibleTravelTag));
                        } else {
                            dto.setTags(List.of(activityTag));
                        }
                        // 문화/박물관 + (무장애)
                    } else if (tourApi.getCat3().equals("A02060100") || tourApi.getCat3().equals("A02060200") || tourApi.getCat3().equals("A02060300") || tourApi.getCat3().equals("A02060400") || tourApi.getCat3().equals("A02060500") || tourApi.getCat3().equals("A02060600") || tourApi.getCat3().equals("A02060700") || tourApi.getCat3().equals("A02060800") || tourApi.getCat3().equals("A02061100")) {
                        if (tourApi.getDisabled()) {
                            dto.setTags(List.of(cultureMuseumTag, accessibleTravelTag));
                        } else {
                            dto.setTags(List.of(cultureMuseumTag));
                        }
                        // 축제/공연 + (무장애)
                    } else if (tourApi.getCat3().equals("A02070100") || tourApi.getCat3().equals("A02070200") || tourApi.getCat3().equals("A02080100") || tourApi.getCat3().equals("A02081200")) {
                        if (tourApi.getDisabled()) {
                            dto.setTags(List.of(festivalPerformanceTag, accessibleTravelTag));
                        } else {
                            dto.setTags(List.of(festivalPerformanceTag));
                        }
                        // 체험여행 + (무장애)
                    } else if (tourApi.getCat3().equals("A02020800") || tourApi.getCat3().equals("A02030100") || tourApi.getCat3().equals("A02030200") || tourApi.getCat3().equals("A02030300") || tourApi.getCat3().equals("A02030400") || tourApi.getCat3().equals("A02030600")) {
                        if (tourApi.getDisabled()) {
                            dto.setTags(List.of(experienceTravelTag, accessibleTravelTag));
                        } else {
                            dto.setTags(List.of(experienceTravelTag));
                        }
                        // 전통시장 + (무장애)
                    } else if (tourApi.getCat3().equals("A04010100") || tourApi.getCat3().equals("A04010900")) {
                        if (tourApi.getDisabled()) {
                            dto.setTags(List.of(traditionalMarketTag, accessibleTravelTag));
                        } else {
                            dto.setTags(List.of(traditionalMarketTag));
                        }
                        // 역사/유적지 + (무장애)
                    } else if (tourApi.getCat2().equals("A0201") || tourApi.getCat3().equals("A02020200") || tourApi.getCat3().equals("A02050200")) {
                        if (tourApi.getDisabled()) {
                            dto.setTags(List.of(historicalSiteTag, accessibleTravelTag));
                        } else {
                            dto.setTags(List.of(historicalSiteTag));
                        }
                        // 휴식시설 + (무장애)
                    } else if (tourApi.getCat3().equals("A02020300") || tourApi.getCat3().equals("A02020400") || tourApi.getCat3().equals("A02020500")) {
                        if (tourApi.getDisabled()) {
                            dto.setTags(List.of(restFacilityTag, accessibleTravelTag));
                        } else {
                            dto.setTags(List.of(restFacilityTag));
                        }
                        // 식도락 + (무장애)
                    } else if (tourApi.getCat2().equals("A0502")) {
                        if (tourApi.getDisabled()) {
                            dto.setTags(List.of(foodTravelTag, accessibleTravelTag));
                        } else {
                            dto.setTags(List.of(foodTravelTag));
                        }
                    }


                    dto.setName(tourApi.getTitle());
                    dto.setPictureLink(tourApi.getFirstimage());
                    if (dto.getPictureLink() == null || dto.getPictureLink().isBlank() || dto.getPictureLink().isEmpty()) {
                        dto.setPictureLink("https://i.ibb.co/XsNmR3Q/url-null.jpg");
                    }
//                dto.setContent(tourApi.getOverview());
                    dto.setApiContent(tourApi.getOverview());
                    dto.setLocation(locationDto);
                    dto.setDisabled(tourApi.getDisabled());
//                dto.setWithPet(tourApi.getWithPet());
                    dto.setContentId(tourApi.getContentid());
                    dto.setIsApiData(true);
                    dto.setAverageRating(0d);
                    dto.setNickname(adminMember.get().getNickname());
                    destinationService.update(destinationId, dto);
                }
            }
        }
    }

    private TourApi convertToEntity(TourApiResponse.Item item) {
        return TourApi.builder()
                .title(item.getTitle())
                .contentid(item.getContentid())
                .tel(item.getTel())
                .addr1(item.getAddr1())
                .addr2(item.getAddr2())
                .sigungucode(item.getSigungucode())
                .firstimage(item.getFirstimage())
                .firstimage2(item.getFirstimage2())
                .mapx(item.getMapx())
                .mapy(item.getMapy())
                .zipcode(item.getZipcode())
                .createdtime(item.getCreatedtime())
                .modifiedtime(item.getModifiedtime())
                .cat1(item.getCat1())
                .cat2(item.getCat2())
                .cat3(item.getCat3())
                .contenttypeid(item.getContenttypeid())
                .homepage(item.getHomepage())
                .overview(item.getOverview())
                .disabled(item.getDisabled())
                .build();
    }

    private synchronized void saveOrUpdateTour(TourApi tourApi) {
        Optional<TourApi> existingTour = tourApiRepository.findByContentid(tourApi.getContentid());
        if (existingTour.isPresent()) {
            if (!existingTour.equals(tourApi)) {
                log.info("[TourApi] 기존 투어 업데이트: contentId={}", tourApi.getContentid());
                TourApi existing = existingTour.get();
                existing.setTitle(tourApi.getTitle());
                existing.setTel(tourApi.getTel());
                existing.setAddr1(tourApi.getAddr1());
                existing.setAddr2(tourApi.getAddr2());
                existing.setSigungucode(tourApi.getSigungucode());
                existing.setFirstimage(tourApi.getFirstimage());
                existing.setFirstimage2(tourApi.getFirstimage2());
                existing.setMapx(tourApi.getMapx());
                existing.setMapy(tourApi.getMapy());
                existing.setZipcode(tourApi.getZipcode());
                existing.setCreatedtime(tourApi.getCreatedtime());
                existing.setModifiedtime(tourApi.getModifiedtime());
                existing.setCat1(tourApi.getCat1());
                existing.setCat2(tourApi.getCat2());
                existing.setCat3(tourApi.getCat3());
                existing.setContenttypeid(tourApi.getContenttypeid());
                existing.setHomepage(tourApi.getHomepage());
                existing.setOverview(tourApi.getOverview());
                existing.setDisabled(tourApi.getDisabled());
                tourApiRepository.save(existing);
            }
        } else {
            log.info("[TourApi] 새로운 투어 저장: contentId={}", tourApi.getContentid());
            tourApiRepository.save(tourApi);
        }
    }
}