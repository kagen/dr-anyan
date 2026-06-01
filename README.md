# Dr Android (Dr. Mario-like)

Android向けのDr.マリオ風パズルゲームです。

## 機能
- 8x16盤面
- 2色カプセルの落下・左右移動・回転
- 同色4つ以上の縦横連結で消去
- 消去後の重力落下
- ウイルス全消去でクリア

## 操作
- `←` : 左移動
- `→` : 右移動
- `回転` : 時計回り回転
- `高速落下` : 一気に着地

## ビルド
```bash
./gradlew assembleDebug
```

APK:
`app/build/outputs/apk/debug/app-debug.apk`