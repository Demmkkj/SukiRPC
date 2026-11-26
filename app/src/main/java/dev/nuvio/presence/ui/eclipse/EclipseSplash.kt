package dev.nuvio.presence.ui.eclipse

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.splashscreen.SplashScreen

// The platform decides when the first frame is ready. No artificial startup delay.
internal fun configureEclipseSplash(splash:SplashScreen){
    splash.setOnExitAnimationListener { provider ->
        if(!ValueAnimator.areAnimatorsEnabled()){
            provider.remove()
        }else{
            val icon=provider.iconView
            val scaleX=ObjectAnimator.ofFloat(icon,View.SCALE_X,1f,1.10f).apply{
                duration=220;interpolator=OvershootInterpolator(0.85f)
            }
            val scaleY=ObjectAnimator.ofFloat(icon,View.SCALE_Y,1f,1.10f).apply{
                duration=220;interpolator=OvershootInterpolator(0.85f)
            }
            val fade=ObjectAnimator.ofFloat(provider.view,View.ALPHA,1f,0f).apply{
                duration=220;interpolator=DecelerateInterpolator()
            }
            AnimatorSet().apply{
                playTogether(scaleX,scaleY,fade)
                addListener(object:AnimatorListenerAdapter(){
                    private var removed=false
                    private fun finish(){if(!removed){removed=true;provider.remove()}}
                    override fun onAnimationEnd(animation:Animator)=finish()
                    override fun onAnimationCancel(animation:Animator)=finish()
                })
                start()
            }
        }
    }
}
