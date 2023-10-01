package maengmaeng.gamelogicservice.gameRoom.service;

import lombok.AllArgsConstructor;
import maengmaeng.gamelogicservice.gameRoom.domain.*;
import maengmaeng.gamelogicservice.gameRoom.domain.db.*;
import maengmaeng.gamelogicservice.gameRoom.domain.dto.AfterMoveResponse;
import maengmaeng.gamelogicservice.gameRoom.domain.dto.Dice;
import maengmaeng.gamelogicservice.gameRoom.domain.dto.GameStart;
import maengmaeng.gamelogicservice.gameRoom.domain.dto.PlayerSeq;
import maengmaeng.gamelogicservice.gameRoom.repository.*;
import maengmaeng.gamelogicservice.global.dto.ResponseDto;

import org.apache.coyote.Response;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class GameRoomService {

	private final DbCountryRespository dbCountryRespository;
	private final DbNewsRepository dbNewsRepository;
	private final DbStockRepository dbStockRepository;
	private final DbNewsCountryRepository dbNewsCountryRepository;
	private final DbCardRepository dbCardRepository;
	private final DbNewsStockRepository dbNewsStockRepository;
	private final GameInfoRepository gameInfoRepository;
	private final GameInfoMapper gameInfoMapper;
	private final AvatarRepository avatarRepository;
	private static final int stopTrade = 8;

	/**
	 * 게임 정보  가져오기
	 * Params: roomCode
	 * return GameInfo
	 * */
	public GameInfo getInfo(String roomCode) {
		return gameInfoRepository.getGameInfo(roomCode);

	}

	/**
	 * 처음 시작 카드 선택
	 * */
	public GameStart setStart(String roomCode, int playerCnt) {

		StartCard[] cards = new StartCard[playerCnt];
		for (int i = 0; i < playerCnt; i++) {
			cards[i] = StartCard.builder().seq(i + 1).selected(false).build();
		}
		Random random = new Random();
		for (int i = cards.length - 1; i > 0; i--) {
			int j = random.nextInt(i + 1); // 0부터 i까지 무작위 인덱스 선택
			// i와 j 위치의 요소 교환
			StartCard temp = cards[i];
			cards[i] = cards[j];
			cards[j] = temp;
		}

		List<DbCountry> dbCountryList = dbCountryRespository.findAll();

		List<News> platinumNews = dbNewsRepository.findByNewsType("Platinum")
			.stream()
			.map(gameInfoMapper::toRedisNews)
			.collect(Collectors.toList());
		Collections.shuffle(platinumNews);
		List<News> diamondNews = dbNewsRepository.findByNewsType("Diamond")
			.stream()
			.map(gameInfoMapper::toRedisNews)
			.collect(Collectors.toList());
		Collections.shuffle(diamondNews);
		List<News> bronzeNews = dbNewsRepository.findByNewsType("Bronze")
			.stream()
			.map(gameInfoMapper::toRedisNews)
			.collect(Collectors.toList());
		Collections.shuffle(bronzeNews);
		int bronze = bronzeNews.size();
		int diamond = diamondNews.size();
		int platinum = platinumNews.size();
		List<Land> landList = dbCountryList.stream().map(gameInfoMapper::toRedisLand).collect(Collectors.toList());
		List<Stock> stockList = dbStockRepository.findAll()
			.stream()
			.map(gameInfoMapper::toRedisStock)
			.collect(Collectors.toList());

		GameInfo gameInfo = GameInfo.builder()
			.roomCode(roomCode)
			.players(new Player[4])
			.lands(landList)
			.info(Info.builder().playerCnt(playerCnt).build())
			.goldenKeys(gameInfoMapper.toRedisGoldenKeys(bronze, diamond, platinum))
			.stocks(stockList)
			.newsInfo(NewsInfo.builder().bronze(bronzeNews).diamond(diamondNews).platinum(platinumNews).build())
			.seqCards(cards)
			.build();

		gameInfoRepository.createGameRoom(gameInfo);

		return GameStart.builder().cards(cards).build();

	}

	/**
	 * 플레이어 게임 순서 세팅*/
	@Transactional
	public StartCard[] setPlayer(String roomCode, PlayerSeq playerSeq) {

		GameInfo gameInfo = getInfo(roomCode);
		//
		StartCard[] startCards = gameInfo.getSeqCards();
		//
		Player[] players = gameInfo.getPlayers();
		int playerNum = gameInfo.getInfo().getPlayerCnt();
		Avatar avatar = avatarRepository.getReferenceById(playerSeq.getCharacterId());

		Player player = gameInfoMapper.toReidsPlayer(playerSeq.getUserId(), playerSeq.getNickname(),
			playerSeq.getCharacterId(), avatar.getAvatarImageNoBg());
		if (players[playerSeq.getPlayerCnt() - 1] == null && !startCards[playerSeq.getPlayerCnt() - 1].isSelected()) {
			players[playerSeq.getPlayerCnt() - 1] = player;
			startCards[playerSeq.getPlayerCnt() - 1].setSelected(true);

			if (playerSeq.getPlayerCnt() == 1) {
				Info info = Info.builder()
					.currentPlayer(players[0].getNickname())
					.playerCnt(playerNum)
					.turnCount(1)
					.effectNews(new LinkedList<>())
					.waitingNews(new LinkedList<WaitingNews>())
					.build();
				gameInfo.setInfo(info);
			}
			gameInfo.setSeqCards(startCards);
		}

		gameInfoRepository.createGameRoom(gameInfo);
		return gameInfo.getSeqCards();
	}

	/**
	 * 현재 플레이어 인덱스 가져오기
	 *
	 * */
	public int getPlayerIdx(Player[] players, String currentPlayer) {
		int currentIdx = -1;
		for (int i = 0; i < players.length; i++) {
			if (players[i] != null && players[i].isAlive() && players[i].getNickname().equals(currentPlayer)) {
				currentIdx = i;
			}
		}
		return currentIdx;
	}

	/**
	 * 주사위 눈 반환
	 * */
	public Dice getDice() {
		Random random = new Random();

		// 주사위 1 던지기 (1부터 6까지)
		int dice1 = random.nextInt(6) + 1;

		// 주사위 2 던지기 (1부터 6까지)
		int dice2 = random.nextInt(6) + 1;

		return Dice.builder().dice1(dice1).dice2(dice2).build();
	}

	/**
	 * 주사위 굴리기.
	 * */
	public ResponseDto rollDice(String roomCode) {
		// 게임 정보 가져오기
		GameInfo gameInfo = gameInfoRepository.getGameInfo(roomCode);
		Player[] players = gameInfo.getPlayers();

		String currentPlayer = gameInfo.getInfo().getCurrentPlayer();
		int currentIdx = -1;
		// 현재 플레이어 인덱스 찾기
		for (int i = 0; i < players.length; i++) {
			if (players[i] != null && players[i].isAlive() && players[i].getNickname().equals(currentPlayer)) {
				currentIdx = i;
			}
		}
		if (currentIdx != -1) {
			// 예외 처리
		}
		// 주사위 굴리기
		Dice dice = getDice();
		Player curPlayer = players[currentIdx];

		boolean checkTrade = false;
		// 더블 일 때
		if (dice.getDice1() == dice.getDice2()) {
			int doubleCount = curPlayer.getDoubleCount();
			doubleCount++;
			// 더블이 3번 나오면 거래정지 칸으로 이동 및 턴 종료
			if (doubleCount >= 3) {
				checkTrade = true;
			}
			curPlayer.setDoubleCount(doubleCount);

		}
		ResponseDto responseDto = null;
		int curLocation = curPlayer.getCurrentLocation();

		if (checkTrade) {
			curPlayer.setCurrentLocation(stopTrade);
			// TODO: 거래정지

			players[currentIdx] = curPlayer;
			gameInfo.setPlayers(players);
			gameInfoRepository.createGameRoom(gameInfo);
			dice.setDoubleCount(curPlayer.getDoubleCount());
			// 거래 정지 칸으로 이동
			// 클라이언트에서 서버로 턴종료  호출
			responseDto = ResponseDto.builder().type("거래정지칸도착").data(dice).build();

		} else {
			//
			//            System.out.println("move 호출 전 " + curPlayer.getCurrentLocation());
			Player player = move(curPlayer, dice.getDice1() + dice.getDice2());
			//            System.out.println("move 호출 뒤 " + curPlayer.getCurrentLocation());

			// 한바퀴 돌았으면
			if (curLocation > player.getCurrentLocation()) {
				System.out.println("한바퀴");
				dice.setLapCheck(true);
			}
			players[currentIdx] = player;
			gameInfo.setPlayers(players);
			gameInfoRepository.createGameRoom(gameInfo);
			dice.setDoubleCount(curPlayer.getDoubleCount());

			responseDto = ResponseDto.builder().type("주사위").data(dice).build();

		}

		return responseDto;
	}

	/**
	 *  현재 칸이 거래 정지에 있고 player의 doubleCount가 3이 아닐 때 주사위 굴리기
	 * */
	public GameInfo stopTrade(String roomCode) {
		GameInfo gameInfo = getInfo(roomCode);
		// 더블이면 탈출 or stopTradeCount 값이 3이면 탈출
		Player[] players = gameInfo.getPlayers();

		String currentPlayer = gameInfo.getInfo().getCurrentPlayer();
		int currentIdx = -1;
		// 현재 플레이어 인덱스 찾기
		for (int i = 0; i < players.length; i++) {
			if (players[i] != null && players[i].isAlive() && players[i].getNickname().equals(currentPlayer)) {
				currentIdx = i;
			}
		}
		Player curPlayer = players[currentIdx];
		// 현재 위치가 stopTrade 위치가 아니면
		if (curPlayer.getCurrentLocation() != stopTrade) {
			// 예외 처리
			System.out.println("예외");
		}

		// 주사위 던지기
		Dice dice = getDice();

		// TODO:  더블이 아니면 턴 종료
		if (dice.getDice1() != dice.getDice2()) {

			//            return gameInfo;
		}
		// TODO: 더블이면 이동
		Player player = move(curPlayer, dice.getDice1() + dice.getDice2());

		return gameInfo;
	}

	/**
	 * 토지 및 건물 구매
	 * @param roomCode
	 * @param purchasedBuildings : 길이 4의 boolean 배열, 순서대로 토지, 건물1, 건물2, 건물 3의 구매 여부를 담은 배열
	 */
	public ResponseDto purchaseAndUpdateGameInfo(String roomCode, boolean[] purchasedBuildings) {
		// 게임 정보 가져오기
		GameInfo gameInfo = gameInfoRepository.getGameInfo(roomCode);
		Player[] players = gameInfo.getPlayers();
		List<Land> lands = gameInfo.getLands();

		String currentPlayer = gameInfo.getInfo().getCurrentPlayer();
		int currentIdx = -1;
		for (int i = 0; i < players.length; i++) {
			if (players[i] != null && players[i].isAlive() && players[i].getNickname().equals(currentPlayer)) {
				currentIdx = i;
			}
		}
		if (currentIdx != -1) {
			// 예외 처리
		}

		//현재 플레이어의 위치 가져오기
		Player curPlayer = players[currentIdx];
		Land curLand = gameInfo.getLands().get(curPlayer.getCurrentLocation());

		// 지불 금액에 땅 값 추가
		int totalPay = curLand.getLandPrice();

		// 지불 금액에 구매한 건물 추가
		int[] buildingPrices = curLand.getBuildingPrices();
		for (int idx = 0; idx < purchasedBuildings.length; idx++) {
			if (purchasedBuildings[idx]) {
				totalPay += buildingPrices[idx];

			}
		}

		curPlayer.setMoney(curPlayer.getMoney() - totalPay);
		curLand.setOwner(currentIdx);
		curLand.setBuildings(purchasedBuildings);

		players[currentIdx] = curPlayer;
		gameInfo.setPlayers(players);

		lands.set(curLand.getLandId(), curLand);
		gameInfo.setLands(lands);

		gameInfoRepository.createGameRoom(gameInfo);

		return ResponseDto.builder()
			.type("자유")
			.data(AfterMoveResponse.builder().players(players).lands(gameInfo.getLands()).build())
			.build();
	}

	/**
	 * 매각. 매각은 건물 단위가 아닌 토지 단위로 이루어진다.
	 * @param roomCode
	 * @param landId 매각하는 토지 ID
	 */
	public ResponseDto forSale(String roomCode, int landId) {
		// 게임 정보 가져오기
		GameInfo gameInfo = gameInfoRepository.getGameInfo(roomCode);
		Player[] players = gameInfo.getPlayers();

		String currentPlayer = gameInfo.getInfo().getCurrentPlayer();
		int currentIdx = -1;
		for (int i = 0; i < players.length; i++) {
			if (players[i] != null && players[i].isAlive() && players[i].getNickname().equals(currentPlayer)) {
				currentIdx = i;
			}
		}
		if (currentIdx != -1) {
			// 예외 처리
		}

		int currentLandPrice = 0;
		Land saledLand = gameInfo.getLands().get(landId);

		for (int i = 0; i < saledLand.getBuildingPrices().length; i++) {
			if (saledLand.getBuildings()[i]) {
				currentLandPrice += saledLand.getCurrentBuildingPrices()[i];
			}
		}

		saledLand.setOwner(-1);
		saledLand.setBuildings(new boolean[] {false, false, false});

		players[currentIdx].setMoney(players[currentIdx].getMoney() + (long)(currentLandPrice * 0.7));

		//gameInfo에 바뀐 정보 최신화
		gameInfo.getLands().set(landId, saledLand);
		gameInfo.setPlayers(players);

		//Redis에 바뀐 정보 업데이트
		gameInfoRepository.createGameRoom(gameInfo);

		return ResponseDto.builder()
			.type("자유")
			.data(AfterMoveResponse.builder().lands(gameInfo.getLands()).players(gameInfo.getPlayers()).build())
			.build();
	}

	/**
	 * 통행료 지불
	 * @param roomCode
	 */
	public ResponseDto payFee(String roomCode) {
		// 게임 정보 가져오기
		GameInfo gameInfo = gameInfoRepository.getGameInfo(roomCode);
		Player[] players = gameInfo.getPlayers();

		String currentPlayer = gameInfo.getInfo().getCurrentPlayer();
		int currentIdx = -1;
		for (int i = 0; i < players.length; i++) {
			if (players[i] != null && players[i].isAlive() && players[i].getNickname().equals(currentPlayer)) {
				currentIdx = i;
			}
		}
		if (currentIdx != -1) {
			// 예외 처리
		}

		//플레이어의 현재 위치
		int landId = players[currentIdx].getCurrentLocation();

		Land currentLand = gameInfo.getLands().get(landId);
		int currentLandFee = currentLand.getCurrentFees()[0];

		for (int i = 0; i < currentLand.getCurrentFees().length; i++) {
			if (currentLand.getBuildings()[i]) {
				currentLandFee += currentLand.getCurrentFees()[i + 1];
			}
		}

		// 통행료 만큼 현재 플레이어의 보유 자산 및 보유 현금 차감
		players[currentIdx].setMoney(players[currentIdx].getMoney() - currentLandFee);
		players[currentIdx].setAsset(players[currentIdx].getAsset() - currentLandFee);

		// 통행료 만큼 땅 주인의 보유 자산 및 보유 현금 증감
		int owner = currentLand.getOwner();
		players[owner].setMoney(players[owner].getMoney() + currentLandFee);
		players[owner].setAsset(players[owner].getAsset() + currentLandFee);

		// 바뀐 정보 gamInfo에 업데이트
		gameInfo.setPlayers(players);

		gameInfoRepository.createGameRoom(gameInfo);

		return ResponseDto.builder().type("인수").data(players).build();
	}

	/**
	 * 인수
	 * @param roomCode
	 */
	public ResponseDto takeOver(String roomCode) {
		// 게임 정보 가져오기
		GameInfo gameInfo = gameInfoRepository.getGameInfo(roomCode);
		Player[] players = gameInfo.getPlayers();

		String currentPlayer = gameInfo.getInfo().getCurrentPlayer();
		int currentIdx = -1;
		for (int i = 0; i < players.length; i++) {
			if (players[i] != null && players[i].isAlive() && players[i].getNickname().equals(currentPlayer)) {
				currentIdx = i;
			}
		}
		if (currentIdx != -1) {
			// 예외 처리
		}

		//플레이어의 현재 위치
		int landId = players[currentIdx].getCurrentLocation();

		Land currentLand = gameInfo.getLands().get(landId);
		int currentLandFee = currentLand.getCurrentFees()[0];

		for (int i = 0; i < currentLand.getCurrentFees().length; i++) {
			if (currentLand.getBuildings()[i]) {
				currentLandFee += currentLand.getCurrentFees()[i + 1];
			}
		}

		// 통행료 만큼 현재 플레이어의 보유 자산 및 보유 현금 차감
		players[currentIdx].setMoney(players[currentIdx].getMoney() - currentLandFee);
		players[currentIdx].setAsset(players[currentIdx].getAsset() - currentLandFee);

		// 통행료 만큼 땅 주인의 보유 자산 및 보유 현금 증감
		int owner = currentLand.getOwner();
		players[owner].setMoney(players[owner].getMoney() + currentLandFee);
		players[owner].setAsset(players[owner].getAsset() + currentLandFee);

		// 인수 로직 처리 후 땅 주인 변경
		currentLand.setOwner(currentIdx);

		//gameInfo에 바뀐 정보 최신화
		gameInfo.setPlayers(players);
		gameInfo.getLands().set(currentLand.getLandId(), currentLand);

		//Redis에 gameInfo 업데이트
		gameInfoRepository.createGameRoom(gameInfo);

		// 바뀐 정보 return
		return ResponseDto.builder()
			.type("자유")
			.data(AfterMoveResponse.builder().lands(gameInfo.getLands()).players(gameInfo.getPlayers()).build())
			.build();
	}

	/**
	 * 맹맹 지급
	 * */
	public ResponseDto maengMaeng(/*(Player player, List<Stock> stocks, List<Land> lands,*/ GameInfo gameInfo) {
		// 맹맹: 보유 현금 * 0.15 + 배당금 - 대출 원금 * 0.24)

		Player[] players = gameInfo.getPlayers();
		int playerIdx = getPlayerIdx(players, gameInfo.getInfo().getCurrentPlayer());
		Player player = players[playerIdx];
		List<Land> lands = gameInfo.getLands();
		List<Stock> stocks = gameInfo.getStocks();
		long maengMaeng = 0;
		long money = Math.round(player.getMoney() * 0.15);
		long dividends = 0;
		long loan = Math.round(player.getLoan() * 0.24);
		int[] playerStock = player.getStocks();
		// 배당금 구하기
		for (int i = 1; i < playerStock.length; i++) {
			dividends += playerStock[i] * stocks.get(i - 1).getDividends();
		}
		maengMaeng = money + dividends - loan;
		// 맹맹 >=0 이면 보유 현금 +
		if (maengMaeng >= 0) {
			long playerMoney = player.getMoney();
			player.setMoney(playerMoney + maengMaeng);
			players[playerIdx] = player;
			gameInfo.setPlayers(players);
			gameInfoRepository.createGameRoom(gameInfo);
			return ResponseDto.builder().type("맹맹지급").data(player).build();

		} else {
			// 맹맹이 음수일 때
			// 맹맹이 보유자산 보다 많을 때?
			if (maengMaeng > calculateMoney(player, stocks, lands)) {
				//TODO: 파산 절차
				return ResponseDto.builder().type("파산").build();

			} else {
				if (player.getMoney() - maengMaeng >= 0) {
					// 보유 현금 -
					player.setMoney(player.getMoney() - maengMaeng);
					players[playerIdx] = player;
					gameInfo.setPlayers(players);
					gameInfoRepository.createGameRoom(gameInfo);
					return ResponseDto.builder().type("맹맹지급").data(player).build();

				} else {
					//TODO: 매각 절차
					return ResponseDto.builder().type("매각시작").build();
				}
			}

		}

	}

	/**
	 *  플레이어 화면에 보여줄 총자산 계산
	 * */
	public Long calculateAsset(Player player, List<Stock> stocks, List<Land> lands) {
		//TODO: asset 계산
		long asset = 0;
		long stockMoney = 0;
		// 소유중인 주식 가격 구하기
		int[] playerStock = player.getStocks();
		for (int i = 1; i < playerStock.length; i++) {
			// 주식의 현재 가격 저장
			stockMoney += playerStock[i] * stocks.get(i - 1).getCurrentCost();

		}
		asset += stockMoney;
		long landMoney = 0;
		// 소유 중인 땅 가격 구하기
		List<Integer> landIdx = player.getLands();
		for (int idx : landIdx) {
			// 소유중인 나라 가져와서
			Land land = lands.get(idx);
			// 땅, 건물을 어떤것을 가지고 있는지 확인 후 가격 계산
			boolean[] check = land.getBuildings();
			for (int i = 0; i < check.length; i++) {
				// 땅 가지고 있을 때
				if (i == 0 && check[i]) {
					landMoney += 10000 * land.getCurrentLandPrice();
				}
				if (i == 1 && check[i]) {
					landMoney += 10000 * land.getCurrentBuildingPrices()[0];
				}
				if (i == 2 && check[i]) {
					landMoney += 10000 * land.getCurrentBuildingPrices()[1];
				}
				if (i == 3 && check[i]) {
					landMoney += 10000 * land.getCurrentBuildingPrices()[2];
				}
			}

		}

		asset += landMoney;

		return asset;

	}

	/**
	 * 파산 등에 사용할 자산 계산
	 */

	public long calculateMoney(Player player, List<Stock> stocks, List<Land> lands) {
		long asset = 0;
		long stockMoney = 0;
		// 소유중인 주식 가격 구하기
		int[] playerStock = player.getStocks();
		for (int i = 1; i < playerStock.length; i++) {
			// 주식의 현재 가격 저장
			stockMoney += playerStock[i] * stocks.get(i - 1).getCurrentCost();

		}
		asset += stockMoney;
		long landMoney = 0;
		// 소유 중인 땅 가격 구하기
		List<Integer> landIdx = player.getLands();
		for (int idx : landIdx) {
			// 소유중인 나라 가져와서
			Land land = lands.get(idx);
			// 땅, 건물을 어떤것을 가지고 있는지 확인 후 가격 계산
			boolean[] check = land.getBuildings();
			for (int i = 0; i < check.length; i++) {
				// 땅 가지고 있을 때
				if (i == 0 && check[i]) {
					landMoney += 10000 * land.getCurrentLandPrice();
				}
				if (i == 1 && check[i]) {
					landMoney += 10000 * land.getCurrentBuildingPrices()[0];
				}
				if (i == 2 && check[i]) {
					landMoney += 10000 * land.getCurrentBuildingPrices()[1];
				}
				if (i == 3 && check[i]) {
					landMoney += 10000 * land.getCurrentBuildingPrices()[2];
				}
			}

		}

		asset += landMoney * 0.7;

		return asset;
	}

	/**
	 * 이동 로직
	 * */
	public Player move(Player player, int move) {
		int currentLocation = player.getCurrentLocation();
		int nextLocation = (currentLocation + move) % 32;
		player.setCurrentLocation(nextLocation);
		System.out.println(player.getCurrentLocation());
		return player;

	}

	/**
	 * 턴을 종료하는 로직
	 * 1. 턴을 종료 후  다음 플레이어 확인(죽은 것도 생각)
	 * 2. 마지막 플레이어면 턴 count 올리고
	 * */
	//    public GameInfo endTurn(String roomCode){
	//
	//        GameInfo gameInfo =gameInfoRepository.getGameInfo(roomCode);
	//        // player 리스트
	//
	//        Player[] players= gameInfo.getPlayers();
	//        Info info = gameInfo.getInfo();
	//        String currentPlayer = info.getCurrentPlayer();
	//        System.out.println(currentPlayer);
	//        int playerIdx =-1;
	//        boolean nextTurn = false;
	//        // 현재 플레이어
	//        for(int i=0;i<players.length;i++){
	//            if(players[i].getNickname().equals(currentPlayer)){
	//                playerIdx = i;
	//            }
	//        }
	//        if(playerIdx==-1){
	//            System.out.println("혼자 살아있음");
	//        }
	//        if(playerIdx==3){
	//            // 턴 카운트를 +1
	//            System.out.println("마지막 플레이어");
	//            nextTurn = true;
	//        }
	//        // 살아있는 마지막 플레이어
	//        for(int i=0;i<3;i++){
	//            int nextPlayerIdx = playerIdx+1;
	//
	//            // 플레이어가 살아있
	//            if(players[nextPlayerIdx+i].isAlive()){
	//
	//                break;
	//            }
	//        }
	//
	//
	//
	//
	//        return gameInfo;
	//
	//
	//
	//
	//    }

}
