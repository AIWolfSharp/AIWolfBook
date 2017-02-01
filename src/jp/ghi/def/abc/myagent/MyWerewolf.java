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
import org.aiwolf.sample.lib.AbstractWerewolf;

/**
 * 人狼役エージェントクラス
 */
public class MyWerewolf extends AbstractWerewolf {

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

	// 人狼固有フィールド
	/** 規定人狼数 */
	int numWolves;
	/** 騙る役職 */
	Role fakeRole;
	/** カミングアウトする日 */
	int comingoutDay;
	/** カミングアウトするターン */
	int comingoutTurn;
	/** カミングアウト済みか否か */
	boolean isCameout;
	/** 偽判定結果を入れる待ち行列 */
	Deque<Judge> fakeJudgeQueue = new LinkedList<>();
	/** 偽判定済みエージェントのリスト */
	List<Agent> judgedAgents = new ArrayList<>();
	/** 裏切り者エージェント */
	Agent possessed;
	/** 囁きの待ち行列 */
	Deque<Content> whisperQueue = new LinkedList<>();
	/** 人狼リスト */
	List<Agent> werewolves;
	/** 人間リスト */
	List<Agent> humans;
	/** 襲撃先候補 */
	Agent attackCandidate;
	/** 宣言した襲撃先候補 */
	Agent declaredAttackCandidate;
	/** talk()のターン */
	int talkTurn;

	@Override
	public String getName() {
		return "MyWerewolf";
	}

	@Override
	public void initialize(GameInfo gameInfo, GameSetting gameSetting) {
		me = gameInfo.getAgent();
		myGameInfo = new MyGameInfo(gameInfo);
		werewolves = new ArrayList<>(gameInfo.getRoleMap().keySet());
		numWolves = gameSetting.getRoleNum(Role.WEREWOLF);
		humans = new ArrayList<>(myGameInfo.aliveOthers);
		humans.removeAll(werewolves);
		// ランダムに騙る役職を決める
		List<Role> fakeRoles = new ArrayList<>();
		for (Role role : gameInfo.getExistingRoles()) {
			if (role == Role.VILLAGER || role == Role.SEER || role == Role.MEDIUM) {
				fakeRoles.add(role);
			}
		}
		Collections.shuffle(fakeRoles);
		fakeRole = fakeRoles.get(0);
		whisperQueue.offer(new Content(new ComingoutContentBuilder(me, fakeRole)));
		// 1～3日目からランダムにカミングアウトする
		List<Integer> comingoutDays = new ArrayList<>(Arrays.asList(1, 2, 3));
		Collections.shuffle(comingoutDays);
		comingoutDay = comingoutDays.get(0);
		// 第0～4ターンからランダムにカミングアウトする
		List<Integer> comingoutTurns = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4));
		Collections.shuffle(comingoutTurns);
		comingoutTurn = comingoutTurns.get(0);
		isCameout = false;
		fakeJudgeQueue.clear();
		judgedAgents.clear();
	}

	@Override
	public void update(GameInfo gameInfo) {
		currentGameInfo = gameInfo;
		myGameInfo.update(currentGameInfo);
		List<Agent> possessedPersons = new ArrayList<>();
		List<Judge> judgeList = new ArrayList<>(myGameInfo.divinationList);
		judgeList.addAll(myGameInfo.identList);
		// 占い/霊媒結果が嘘の場合，裏切り者候補
		for (Judge judge : judgeList) {
			if ((humans.contains(judge.getTarget()) && judge.getResult() == Species.WEREWOLF)
					|| (werewolves.contains(judge.getTarget())
							&& judge.getResult() == Species.HUMAN)) {
				if (!werewolves.contains(judge.getAgent())
						&& !possessedPersons.contains(judge.getAgent())) {
					possessedPersons.add(judge.getAgent());
				}
			}
		}
		if (!possessedPersons.isEmpty()) {
			if (!possessedPersons.contains(possessed)) {
				Collections.shuffle(possessedPersons);
				possessed = possessedPersons.get(0);
				whisperQueue
						.offer(new Content(new EstimateContentBuilder(possessed, Role.POSSESSED)));
			}
		}
	}

	@Override
	public void dayStart() {
		declaredVoteCandidate = null;
		voteCandidate = null;
		declaredAttackCandidate = null;
		attackCandidate = null;
		talkQueue.clear();
		talkTurn = -1;
		// 偽の判定
		if (myGameInfo.day > 0) {
			Judge fakeJudge = getFakeJudge(fakeRole);
			if (fakeJudge != null) {
				fakeJudgeQueue.offer(fakeJudge);
				if (fakeRole == Role.SEER) {
					judgedAgents.add(fakeJudge.getTarget());
				}
			}
		}
	}

	@Override
	public String talk() {
		talkTurn++;
		if (fakeRole != Role.VILLAGER) {
			if (!isCameout) {
				// 他の人狼のカミングアウト状況を調べて騙る役職が重複しないようにする
				int fakeSeerCO = 0;
				int fakeMediumCO = 0;
				for (Agent wolf : werewolves) {
					if (wolf != me) {
						if (myGameInfo.comingoutMap.get(wolf) == Role.SEER) {
							fakeSeerCO++;
						} else if (myGameInfo.comingoutMap.get(wolf) == Role.MEDIUM) {
							fakeMediumCO++;
						}
					}
				}
				if ((fakeRole == Role.SEER && fakeSeerCO > 0)
						|| (fakeRole == Role.MEDIUM && fakeMediumCO > 0)) {
					fakeRole = Role.VILLAGER; // 潜伏
					whisperQueue.offer(new Content(new ComingoutContentBuilder(me, Role.VILLAGER)));
				} else {
					// 対抗カミングアウトがある場合，今日カミングアウトする
					for (Agent agent : humans) {
						if (myGameInfo.comingoutMap.get(agent) == fakeRole) {
							comingoutDay = myGameInfo.day;
							break;
						}
					}
					// カミングアウトするタイミングになったらカミングアウト
					if (myGameInfo.day >= comingoutDay && talkTurn >= comingoutTurn) {
						isCameout = true;
						talkQueue.offer(new Content(new ComingoutContentBuilder(me, fakeRole)));
					}
				}
			}
			// カミングアウトしたらこれまでの偽判定結果をすべて公開
			else {
				while (!fakeJudgeQueue.isEmpty()) {
					Judge judge = fakeJudgeQueue.poll();
					if (fakeRole == Role.SEER) {
						talkQueue.offer(new Content(new DivinedResultContentBuilder(
								judge.getTarget(), judge.getResult())));
					} else if (fakeRole == Role.MEDIUM) {
						talkQueue.offer(new Content(
								new IdentContentBuilder(judge.getTarget(), judge.getResult())));
					}
				}
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
		List<Agent> villagers = new ArrayList<>(myGameInfo.aliveOthers);
		villagers.removeAll(werewolves);
		villagers.remove(possessed);
		List<Agent> candidates = villagers; // 村人騙りの場合は村人陣営から
		// 占い師/霊媒師騙りの場合
		if (fakeRole != Role.VILLAGER) {
			candidates = new ArrayList<>();
			// 対抗カミングアウトのエージェントは投票先候補
			for (Agent agent : villagers) {
				if (myGameInfo.comingoutMap.get(agent) == fakeRole) {
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
			candidates.removeAll(myGameInfo.deadAgents);
			// 候補がいなければ人間と判定していない村人陣営から
			if (candidates.isEmpty()) {
				candidates.addAll(villagers);
				candidates.removeAll(fakeHumans);
				// それでも候補がいなければ村人陣営から
				if (candidates.isEmpty()) {
					candidates.addAll(villagers);
				}
			}
		}
		if (!candidates.contains(voteCandidate)) {
			Collections.shuffle(candidates);
			voteCandidate = candidates.get(0);
		}
	}

	/** 偽判定を返す */
	Judge getFakeJudge(Role role) {
		Agent target = null;
		// 占い師騙りの場合
		if (role == Role.SEER) {
			List<Agent> candidates = new ArrayList<>();
			for (Agent agent : myGameInfo.aliveOthers) {
				if (!judgedAgents.contains(agent)
						&& myGameInfo.comingoutMap.get(agent) != Role.SEER) {
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
		}
		// 霊媒師騙りの場合
		else if (role == Role.MEDIUM) {
			target = currentGameInfo.getExecutedAgent();
		}
		if (target != null) {
			Species result = Species.HUMAN;
			// 人間が偽占い対象の場合
			if (humans.contains(target)) {
				// 偽人狼に余裕があれば
				if (MyPossessed.countWolfJudge(fakeJudgeQueue) < numWolves) {
					// 裏切り者，あるいはまだカミングアウトしていないエージェントの場合，判定は五分五分
					if ((target == possessed || !myGameInfo.comingoutMap.containsKey(target))) {
						if (Math.random() < 0.5) {
							result = Species.WEREWOLF;
						}
					}
					// それ以外は人狼判定
					else {
						result = Species.WEREWOLF;
					}
				}
			}
			return new Judge(myGameInfo.day, me, target, result);
		} else {
			return null;
		}
	}

	@Override
	public String whisper() {
		// 以前宣言した（未宣言を含む）襲撃先と違う襲撃先を選んだ場合宣言する
		if (attackCandidate != declaredAttackCandidate) {
			Content content = new Content(new AttackContentBuilder(attackCandidate));
			whisperQueue.offer(content);
			declaredAttackCandidate = attackCandidate;
			// 襲撃を要請する
			whisperQueue.offer(new Content(new RequestContentBuilder(null, content)));
		}
		return whisperQueue.isEmpty() ? Content.SKIP.getText() : whisperQueue.poll().getText();
	}

	@Override
	public Agent attack() {
		// カミングアウトした村人陣営は襲撃先候補
		List<Agent> villagers = new ArrayList<>(myGameInfo.aliveOthers);
		villagers.removeAll(werewolves);
		villagers.remove(possessed);
		List<Agent> candidates = new ArrayList<>();
		for (Agent agent : villagers) {
			if (myGameInfo.comingoutMap.containsKey(agent)) {
				candidates.add(agent);
			}
		}
		// 候補がいなければ村人陣営から
		if (candidates.isEmpty()) {
			candidates.addAll(villagers);
		}
		// 村人陣営がいない場合は裏切り者を襲う
		if (candidates.isEmpty()) {
			candidates.add(possessed);
		}
		if (!candidates.contains(attackCandidate)) {
			Collections.shuffle(candidates);
			attackCandidate = candidates.get(0);
		}
		return attackCandidate;
	}

}
