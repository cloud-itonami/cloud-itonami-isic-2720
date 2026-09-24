# physai-isic-2720 — 電池・蓄電池製造の physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isic-2720`、ISIC 2720 電池・蓄電池製造）に
常駐する bot。仕事は 2 つだけ: **この repo の物理シミュレーションを走らせて物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか

- 手順: UN 38.3 T6（Impact/Crush）の機械的圧壊試験。21700 円筒リチウムイオンセルを、ロボットの圧壊試験セルの
  プレス圧盤が長手軸に垂直に押す想定。
- 実装: `cellworks.robotics/simulate-crush` が `physics-2d/world-step`（固定刻みの剛体インパルスソルバ）で
  圧盤（質量 m）と固定セル（質量 0）の接近・衝突軌跡を時間発展させ、速度変化からピーク減速度と圧壊力 [N]、
  参考値の圧力 [MPa]、最大貫入量 [m] を出す。合否は UN 38.3 T6 の停止力 13 kN を超えるか。
- 測定の入口: `kbb -M:dev:physics`（`cellworks.physics-probe`）。圧盤質量 sweep 5 点（40/80/120/200/300 kg、
  80 と 300 は `cellworks.store` の fixture）の圧壊力と、13 kN を超えない最大圧盤質量（二分法）を EDN 1 行で出す。
  `:count` が `:expected` に満たなければ exit 2 = **測れなかった**（「異常なし」ではない）。

## 分かっている限界（成長の第一候補）

実測（2026-09-24、`kbb -M:dev:physics`）:

1. **ピーク減速度が圧盤質量によらず一定**（全 5 点で 95.238 m/s² = 閉鎖速度 1.0 m/s / dt 0.0105 s）。
   圧壊力は質量に厳密比例するだけ（40 kg → 3809.5 N、300 kg → 28571.4 N）で、境界 136.5 kg も
   13000 / 95.238 の算術で決まる。セルの **力–変位（ケース剛性・座屈・50% 変形までの荷重曲線）を持たない**。
   → セルを剛性 k の非線形ばねとして扱い、荷重を変位から出す形へ育てる（`physics-2d` に無い力要素は
   この repo 内に純関数で持ち、上流へ出す価値があれば提案だけする）。
2. **最大貫入量が常に 0.1 mm で一定**。UN 38.3 T6 の停止条件 (c)「元寸法の 50% 変形」= 10.5 mm に対し
   2 桁小さく、1 tick で止まる衝突モデルでは変形停止条件 (c) を一度も評価できていない。停止条件 (b)
   電圧 100 mV 低下も未モデル。
3. 閉鎖速度 1.0 m/s は規格の 1.5 cm/s ではない開示済みのアナログ値。力–変位モデルが入ったら規格速度へ戻す候補。
4. 13 kN は二次資料で裏付けた値（ns docstring に開示）。UNECE Manual of Tests and Criteria の一次資料を
   引けたら出典を差し替える。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. 上の「分かっている限界」を 1 歩進める。
3. この業種で標準的な物理試験・工程（例: UN 38.3 T4 振動試験、T5 衝撃試験、IEC 62133-2 の落下試験、
   釘刺し試験、化成充電の発熱）を 1 つ、既存の robotics と同じ形（純関数 + governor が独立に再計算できる形 + test）で
   足し、probe の出力に加える。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isic-2720 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:dev:test → kbb -M:dev:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isic-2720 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で schema を保つ。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・閾値を緩める・probe の sweep を減らす）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は simulation が出したものだけ。定数を変えるなら出典（規格番号・URL）を docstring に書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:actuation/ship-cell-batch` などの
  `:safety-critical` な actuation は人の承認なしに commit されない設計を崩さない。
- この repo 以外（上流ライブラリ・他の actor）は編集しない。必要なら報告に「上流にこれが要る」と書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。
