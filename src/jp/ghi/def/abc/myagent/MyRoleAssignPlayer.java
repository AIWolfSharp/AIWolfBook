package jp.ghi.def.abc.myagent;

import org.aiwolf.sample.lib.AbstractRoleAssignPlayer;
import org.aiwolf.sample.player.*;

/**
 * 役職に実際のプレイヤークラスを割り当てるプレイヤークラス
 */
public class MyRoleAssignPlayer extends AbstractRoleAssignPlayer {

	public MyRoleAssignPlayer() {
		setVillagerPlayer(new MyVillager());
		setBodyguardPlayer(new MyBodyguard());
		setMediumPlayer(new MyMedium());
		setSeerPlayer(new MySeer());
		setPossessedPlayer(new MyPossessed());
		setWerewolfPlayer(new MyWerewolf());
	}

	public String getName() {
		return "MyRoleAssignPlayer";
	}

}
