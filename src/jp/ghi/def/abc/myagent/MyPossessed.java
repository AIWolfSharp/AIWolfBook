package jp.ghi.def.abc.myagent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import org.aiwolf.client.lib.*;
import org.aiwolf.common.data.*;
import org.aiwolf.common.net.*;
import org.aiwolf.sample.lib.AbstractPossessed;

/**
 * 裏切り者役エージェントクラス
 */
public class MyPossessed extends AbstractPossessed {

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

	// 裏切り者固有フィールド
	/** 規定人狼数 */
	int numWolves;
	/** カミングアウトする日 */
	int comingoutDay;
	List<Integer> comingoutDays = new ArrayList<>(Arrays.asList(1, 2, 3));
	/** カミングアウト済みか否か */
	boolean isCameout;
	/** 偽判定結果を入れる待ち行列 */
	Deque<Judge> fakeJudgeQueue = new LinkedList<>();
	/** 偽判定済みエージェントのリスト */
	List<Agent> judgedAgents = new ArrayList<>();

	@Override
	public String getName() {
		return "MyPossessed";
	}

	@Override
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		me = gameInfo.getAgent();
		myGameInfo = new MyGameInfo(gameInfo);
		werewolves.clear();
		numWolves = gameSetting.getRoleNum(Role.WEREWOLF);
		comingoutDay = 0; // 早く人狼に気づいてもらうため即カミングアウト
		isCameout = false;
		fakeJudgeQueue.clear();
		judgedAgents.clear();
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
		// 偽の判定
		if (myGameInfo.day > 0) {
			Judge judge = getFakeJudge();
			if (judge != null) {
				fakeJudgeQueue.offer(judge);
				judgedAgents.add(judge.getTarget());
			}
		}
	}

	@Override
	public String talk() {
		// カミングアウトする日になったらカミングアウト
		if (!isCameout && myGameInfo.day >= comingoutDay) {
			talkQueue.offer(new Content(new ComingoutContentBuilder(me, Role.SEER)));
			isCameout = true;
		}
		// カミングアウトしたらこれまでの偽判定結果をすべて公開
		if (isCameout) {
			while (!fakeJudgeQueue.isEmpty()) {
				Judge judge = fakeJudgeQueue.poll();
				talkQueue.offer(new Content(
						new DivinedResultContentBuilder(judge.getTarget(), judge.getResult())));
			}
		}
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
		// 自分や死亡したエージェントを人狼と判定していて，生存している占い師は人狼候補
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
		List<Agent> villagers = new ArrayList<>(myGameInfo.aliveOthers);
		villagers.removeAll(werewolves);
		List<Agent> candidates = new ArrayList<>();
		// 対抗カミングアウトのエージェントは投票先候補
		for (Agent agent : villagers) {
			if (myGameInfo.comingoutMap.get(agent) == Role.SEER) {
				candidates.add(agent);
			}
		}
		// 人狼と判定したエージェントは投票先候補
		List<Agent> fakeHumans = new ArrayList<>();
		for (Judge judge : fakeJudgeQueue) {
			if (judge.getResult() == Species.HUMAN) {
				fakeHumans.add(judge.getTarget());
			} else if (!candidates.contains(judge.getTarget())) {
				candidates.add(judge.getTarget());
			}
		}
		// 候補がいなければ人間と判定していない村人陣営から
		if (candidates.isEmpty()) {
			candidates.addAll(villagers);
			candidates.removeAll(fakeHumans);
			// それでも候補がいなければ村人陣営から
			if (candidates.isEmpty()) {
				candidates.addAll(villagers);
			}
		}
		if (!candidates.contains(voteCandidate)) {
			Collections.shuffle(candidates);
			voteCandidate = candidates.get(0);
		}
	}

	/** 偽判定を返す */
	Judge getFakeJudge() {
		Agent target = null;
		List<Agent> candidates = new ArrayList<>();
		for (Agent agent : myGameInfo.aliveOthers) {
			if (!judgedAgents.contains(agent) && myGameInfo.comingoutMap.get(agent) != Role.SEER) {
				candidates.add(agent);
			}
		}
		if (!candidates.isEmpty()) {
			Collections.shuffle(candidates);
			target = candidates.get(0);
		} else {
			candidates.addAll(myGameInfo.aliveOthers);
			Collections.shuffle(candidates);
			target = candidates.get(0);
		}
		if (target != null) {
			// 偽人狼に余裕があれば，人狼と人間の割合を勘案して，30%の確率で人狼と判定
			Species result = Species.HUMAN;
			if (countWolfJudge(fakeJudgeQueue) < numWolves && Math.random() < 0.3) {
				result = Species.WEREWOLF;
			}
			return new Judge(myGameInfo.day, me, target, result);
		} else {
			return null;
		}
	}

	/** Judgeの待ち行列中の判定が人狼であるものの数を返す */
	static int countWolfJudge(Deque<Judge> judges) {
		int count = 0;
		for (Judge judge : judges) {
			if (judge.getResult() == Species.WEREWOLF) {
				count++;
			}
		}
		return count;
	}

}
