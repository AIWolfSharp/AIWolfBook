package jp.ghi.def.abc.myagent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aiwolf.client.lib.Content;
import org.aiwolf.common.data.*;
import org.aiwolf.common.net.GameInfo;

/** 自分専用のゲーム情報 */
class MyGameInfo {

	/** 日付 */
	int day;
	/** 自分以外のエージェント */
	List<Agent> others;
	/** 自分以外の生存エージェント */
	List<Agent> aliveOthers;
	/** 死亡したエージェント */
	List<Agent> deadAgents = new ArrayList<>();
	/** 追放されたエージェント */
	List<Agent> executedAgentList = new ArrayList<>();
	/** 殺されたエージェント */
	List<Agent> killedAgentList = new ArrayList<>();
	/** 昨日殺されたエージェント */
	Agent lastKilledAgent;
	/** カミングアウト状況 */
	Map<Agent, Role> comingoutMap = new HashMap<>();
	/** 推定宣言状況 */
	Map<Agent, List<Talk>> estimateMap = new HashMap<>();
	/** 占い報告リスト */
	List<Judge> divinationList = new ArrayList<>();
	/** 霊媒報告リスト */
	List<Judge> identList = new ArrayList<>();
	/** talkList読み込みのヘッド */
	private int talkListHead;

	/** GameInfoに基づいてMyGameinfoを構築する */
	MyGameInfo(GameInfo gameInfo) {
		day = -1;
		others = new ArrayList<>(gameInfo.getAgentList());
		others.remove(gameInfo.getAgent());
		aliveOthers = new ArrayList<>(others);
	}

	/** GameInfoに基づいてMyGameinfoを更新する */
	void update(GameInfo gameInfo) {
		// 1日の最初の呼び出しではその日の初期化などを行う
		if (gameInfo.getDay() != day) {
			day = gameInfo.getDay();
			talkListHead = 0;
			addExecutedAgent(gameInfo.getExecutedAgent()); // 前日に追放されたエージェントを登録
			if (!gameInfo.getLastDeadAgentList().isEmpty()) {
				lastKilledAgent = gameInfo.getLastDeadAgentList().get(0); // 妖狐がいないので長さ最大1
			}
			if (lastKilledAgent != null) {
				if (!killedAgentList.contains(lastKilledAgent)) {
					killedAgentList.add(lastKilledAgent);
				}
				aliveOthers.remove(lastKilledAgent);
				if (!deadAgents.contains(lastKilledAgent)) {
					deadAgents.add(lastKilledAgent);
				}
			}
		}

		// （夜フェーズ限定）追放されたエージェントを登録
		addExecutedAgent(gameInfo.getLatestExecutedAgent());
		// talkListからカミングアウト，占い結果，霊媒結果を抽出
		for (int i = talkListHead; i < gameInfo.getTalkList().size(); i++) {
			Talk talk = gameInfo.getTalkList().get(i);
			Agent talker = talk.getAgent();
			Content content = new Content(talk.getText());
			Agent target = content.getTarget();
			switch (content.getTopic()) {
			case COMINGOUT:
				comingoutMap.put(talker, content.getRole());
				break;
			case DIVINED:
				divinationList.add(new Judge(day, talker, content.getTarget(), content.getResult()));
				break;
			case IDENTIFIED:
				identList.add(new Judge(day, talker, target, content.getResult()));
				break;
			case ESTIMATE:
				if (target != null) {
					if (estimateMap.get(target) == null) {
						estimateMap.put(target, new ArrayList<Talk>());
					}
					estimateMap.get(target).add(talk);
				}
				break;
			default:
				break;
			}
		}
		talkListHead = gameInfo.getTalkList().size();
	}

	/** エージェントを追放されたエージェントのリストに追加する。死亡者・生存者リストも更新する */
	private void addExecutedAgent(Agent executedAgent) {
		if (executedAgent != null) {
			if (!executedAgentList.contains(executedAgent)) {
				executedAgentList.add(executedAgent);
			}
			aliveOthers.remove(executedAgent);
			if (!deadAgents.contains(executedAgent)) {
				deadAgents.add(executedAgent);
			}
		}
	}

}
