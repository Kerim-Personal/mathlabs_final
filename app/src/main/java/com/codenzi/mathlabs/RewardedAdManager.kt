package com.codenzi.mathlabs

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object RewardedAdManager {

    private var rewardedAd: RewardedAd? = null
    private const val TAG = "RewardedAdManager"

    fun loadAd(context: Context) {
        val adUnitId = context.getString(R.string.admob_rewarded_unit_id)
        if (adUnitId.isEmpty()) {
            Log.e(TAG, "Rewarded Ad Unit ID is not configured.")
            return
        }

        RewardedAd.load(context, adUnitId, AdManagerAdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, adError.message)
                rewardedAd = null
            }

            override fun onAdLoaded(ad: RewardedAd) {
                Log.d(TAG, "Ad was loaded.")
                rewardedAd = ad
            }
        })
    }

    fun showAd(activity: Activity, onRewardEarned: () -> Unit, onAdFailedToShow: () -> Unit) {
        if (rewardedAd != null) {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Ad was dismissed.")
                    // Yeni bir reklam yükle
                    loadAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d(TAG, "Ad failed to show.")
                    rewardedAd = null
                    onAdFailedToShow()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Ad showed fullscreen content.")
                    rewardedAd = null
                }
            }
            rewardedAd?.show(activity) {
                Log.d(TAG, "User earned the reward.")
                onRewardEarned()
            }
        } else {
            Log.d(TAG, "The rewarded ad wasn't ready yet.")
            onAdFailedToShow()
            // Reklam hazır değilse, tekrar yüklemeyi dene
            loadAd(activity)
        }
    }
}