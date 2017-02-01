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
import org.aiwolf.sample.lib.AbstractSeer;

/**
 * 占い師役エージェントクラス
 */
public class MySeer extends AbstractSeer {

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
	/** 発言の待ち行列 */
	Deque<Content> talkQueue = new LinkedList<>();

	// 占い師固有フィールド
	/** カミングアウトする日 */
	int comingoutDay;
	List<Integer> comingoutDays = new ArrayList<>(Arrays.asList(1, 2, 3));
	/** カミングアウト済みか否か */
	boolean isCameout;
	/** 占い結果を入れる待ち行列 */
	Deque<Judge> divinationQueue = new LinkedList<>();
	/** 人間リスト */
	List<Agent> humans = new ArrayList<>();
	/** 人狼リスト */
	List<Agent> realWolves = new ArrayList<>();
	/** 人狼候補リスト */
	List<Agent> semiWolves = new ArrayList<>();

	@Override
	public String getName() {
		return "MySeer";
	}

	@Override
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		me = gameInfo.getAgent();
		myGameInfo = new MyGameInfo(gameInfo);
		humans.clear();
		realWolves.clear();
		semiWolves.clear();
		Collections.shuffle(comingoutDays);
		comingoutDay = comingoutDays.get(0); // 1～3日目をランダムで
		isCameout = false;
		divinationQueue.clear();
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
		// 占い結果を待ち行列に入れる
		Judge divination = currentGameInfo.getDivineResult();
		if (divination != null) {
			divinationQueue.offer(divination);
			if (divination.getResult() == Species.HUMAN) {
				humans.add(divination.getTarget());
			} else {
				realWolves.add(divination.getTarget());
			}
		}
	}

	@Override
	public String talk() {
		// カミングアウトする日になったら，あるいは占い結果が人狼だったら
		// あるいは占い師カミングアウトが出たらカミングアウト
		if (!isCameout && (myGameInfo.day >= comingoutDay
				|| (!divinationQueue.isEmpty()
						&& divinationQueue.poll().getResult() == Species.WEREWOLF)
				|| myGameInfo.comingoutMap.containsValue(Role.SEER))) {
			talkQueue.offer(new Content(new ComingoutContentBuilder(me, Role.SEER)));
			isCameout = true;
		}
		// カミングアウトしたらこれまでの占い結果をすべて公開
		if (isCameout) {
			while (!divinationQueue.isEmpty()) {
				Judge divination = divinationQueue.poll();
				talkQueue.offer(new Content(new DivinedResultContentBuilder(divination.getTarget(),
						divination.getResult())));
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
		// 生存人狼がいれば当然投票
		List<Agent> aliveWolves = new ArrayList<>(realWolves);
		aliveWolves.removeAll(myGameInfo.deadAgents);
		if (!aliveWolves.isEmpty()) {
			// 既定の投票先が生存人狼でない場合投票先を変える
			if (!aliveWolves.contains(voteCandidate)) {
				Collections.shuffle(aliveWolves);
				voteCandidate = aliveWolves.get(0);
				talkQueue.offer(new Content(new RequestContentBuilder(null,
						new Content(new VoteContentBuilder(voteCandidate)))));
			}
		}
		// 確定人狼はいないので推測する
		else {
			semiWolves.clear();
			// 占い師をカミングアウトしている他のエージェントは人狼候補
			for (Agent agent : myGameInfo.others) {
				if (myGameInfo.comingoutMap.get(agent) == Role.SEER) {
					semiWolves.add(agent);
				}
			}
			// 自分の占い結果と異なる判定の霊媒師は人狼候補
			for (Judge judge : myGameInfo.identList) {
				for (Judge myJudge : divinationQueue) {
					if (judge.getTarget() == myJudge.getTarget()
							&& judge.getResult() != myJudge.getResult()) {
						Agent agent = judge.getAgent();
						if (!semiWolves.contains(agent)) {
							semiWolves.add(agent);
						}
					}
				}
			}
			semiWolves.removeAll(humans);
			semiWolves.removeAll(myGameInfo.deadAgents);
			// 人狼候補がいる場合
			if (!semiWolves.isEmpty()) {
				// 以前の投票先から変わる場合，新たに推測発言と占い宣言をする
				if (!semiWolves.contains(voteCandidate)) {
					Collections.shuffle(semiWolves);
					voteCandidate = semiWolves.get(0);
					talkQueue.offer(
							new Content(new EstimateContentBuilder(voteCandidate, Role.WEREWOLF)));
					talkQueue.offer(new Content(new DivinationContentBuilder(voteCandidate)));
				}
			}
			// 人狼候補がいない場合
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

	@Override
	public Agent divine() {
		// 人狼候補がいればそれらからランダムに占う
		if (!semiWolves.isEmpty()) {
			Collections.shuffle(semiWolves);
			return semiWolves.get(0);
		}
		// 人狼候補がいない場合，まだ占っていない生存者からランダムに占う
		List<Agent> candidates = new ArrayList<>(myGameInfo.aliveOthers);
		candidates.removeAll(realWolves);
		candidates.removeAll(humans);
		if (candidates.isEmpty()) {
			return null;
		}
		Collections.shuffle(candidates);
		return candidates.get(0);
	}

}
