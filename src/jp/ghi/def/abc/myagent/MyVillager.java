package jp.ghi.def.abc.myagent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import org.aiwolf.client.lib.*;
import org.aiwolf.common.data.*;
import org.aiwolf.common.net.*;
import org.aiwolf.sample.lib.AbstractVillager;

/**
 * 村人役エージェントクラス
 */
public class MyVillager extends AbstractVillager {

	// 役職共通フィールド
	/** このエージェント */
	Agent me;
	/** ゲーム情報 */
	GameInfo currentGameInfo;
	/** 独自のゲーム情報 */
	MyGameInfo myGameInfo;
	/** 投票先候補 */
	Agent voteCandidate;
	/** 宣言した投票先 */
	Agent declaredVoteCandidate;
	/** 人狼候補リスト */
	List<Agent> werewolves = new ArrayList<>();
	/** 発言の待ち行列 */
	Deque<Content> talkQueue = new LinkedList<>();

	@Override
	public String getName() {
		return "MyVillager";
	}

	@Override
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		me = gameInfo.getAgent();
		myGameInfo = new MyGameInfo(gameInfo);
		werewolves.clear();
	}

	@Override
	public void update(GameInfo gameInfo) {
		currentGameInfo = gameInfo;
		myGameInfo.update(currentGameInfo);
	}

	@Override
	public void dayStart() {
		declaredVoteCandidate = null;
		voteCandidate = null;
		talkQueue.clear();
	}

	@Override
	public String talk() {
		// 選んだ投票先が以前宣言した（未宣言を含む）投票先と違う場合宣言する
		chooseVoteCandidate();
		if (voteCandidate != declaredVoteCandidate) {
			talkQueue.offer(new Content(new VoteContentBuilder(voteCandidate)));
			declaredVoteCandidate = voteCandidate;
		}
		return talkQueue.isEmpty() ? Content.SKIP.getText() : talkQueue.poll().getText();
	}

	@Override
	public Agent vote() {
		return voteCandidate;
	}

	@Override
	public void finish() {
	}

	/** 投票先候補を選ぶ */
	void chooseVoteCandidate() {
		// 自分や死亡したエージェントを人狼と判定していて，生存している占い師を投票先候補とする
		werewolves.clear();
		for (Judge judge : myGameInfo.divinationList) {
			Agent agent = judge.getAgent();
			if ((judge.getTarget() == me || myGameInfo.killedAgentList.contains(judge.getTarget()))
					&& judge.getResult() == Species.WEREWOLF) {
				if (!werewolves.contains(agent)) {
					werewolves.add(agent);
				}
			}
		}
		List<Agent> candidates = new ArrayList<>(werewolves);
		candidates.removeAll(myGameInfo.deadAgents);
		// 投票先候補が見つかった場合
		if (!candidates.isEmpty()) {
			// 以前の投票先から変わる場合，新たに推測発言と占い要請をする
			if (!candidates.contains(voteCandidate)) {
				Collections.shuffle(candidates);
				voteCandidate = candidates.get(0);
				talkQueue.offer(
						new Content(new EstimateContentBuilder(voteCandidate, Role.WEREWOLF)));
				talkQueue.offer(new Content(new RequestContentBuilder(null,
						new Content(new DivinationContentBuilder(voteCandidate)))));
			}
		}
		// 投票先候補が見つからなかった場合
		else {
			// 既定の投票先があれば投票先はそのまま。投票先未定の場合自分以外の生存者から投票先を選ぶ
			if (voteCandidate == null) {
				List<Agent> aliveOthers = new ArrayList<>(myGameInfo.aliveOthers);
				Collections.shuffle(aliveOthers);
				voteCandidate = aliveOthers.get(0);
			}
		}
	}

}
