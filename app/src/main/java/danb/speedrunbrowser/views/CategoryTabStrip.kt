package danb.speedrunbrowser.views

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.ViewPager
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.objects.Category
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.Level
import danb.speedrunbrowser.api.objects.Variable
import danb.speedrunbrowser.utils.LeaderboardPagerAdapter
import kotlinx.android.synthetic.main.game_list.view.*
import kotlin.math.floor

class CategoryTabStrip(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs), ViewPager.OnPageChangeListener {

    private var mGame: Game? = null

    private val mHsvCategory = IgnorantHorizontalScrollView(context)
    private val mLayoutCategory = LinearLayout(context)

    private val mHsvLevel = IgnorantHorizontalScrollView(context)
    private val mLayoutLevel = LinearLayout(context)

    private var mPager: ViewPager? = null
    var pagerAdapter: LeaderboardPagerAdapter? = null
        private set

    private var mHighlightCategory = 0
    private var mHighlightLevel = 0

    init {

        orientation = VERTICAL

        mHsvCategory.isHorizontalScrollBarEnabled = false
        mHsvLevel.isHorizontalScrollBarEnabled = false

        mLayoutCategory.orientation = HORIZONTAL
        mLayoutCategory.orientation = HORIZONTAL

        mHsvCategory.addView(mLayoutCategory)
        mHsvLevel.addView(mLayoutLevel)

        mHsvLevel.visibility = View.GONE

        addView(mHsvCategory)
        addView(mHsvLevel)
    }

    fun setup(game: Game, selections: Variable.VariableSelections, vp: ViewPager, fm: FragmentManager) {
        mPager = vp
        mPager!!.addOnPageChangeListener(this)

        mGame = game

        pagerAdapter = LeaderboardPagerAdapter(fm, mGame!!, selections, mPager!!)
        mPager!!.adapter = pagerAdapter

        applyTabs()

        onPageSelected(0)
    }

    fun selectLeaderboard(category: Category, level: Level?) {
        mPager!!.currentItem = pagerAdapter!!.indexOf(category, level)
    }

    private fun styleTab(tv: TextView) {
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.isAllCaps = true

        tv.height = resources.getDimensionPixelSize(R.dimen.tab_height)
        tv.gravity = Gravity.CENTER_VERTICAL

        tv.setPadding(resources.getDimensionPixelSize(R.dimen.half_fab_margin), 0, resources.getDimensionPixelSize(R.dimen.half_fab_margin), 0)

        val lp = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = resources.getDimensionPixelSize(R.dimen.half_fab_margin)
        lp.rightMargin = resources.getDimensionPixelSize(R.dimen.half_fab_margin)
        tv.layoutParams = lp
    }

    private fun applyTabs() {
        mLayoutCategory.removeAllViews()
        mLayoutLevel.removeAllViews()

        for (category in pagerAdapter!!.sortedCategories) {
            val tv = TextView(context)

            tv.text = category.name
            styleTab(tv)

            if(category.type == "per-level")
                tv.background = ColorDrawable(ContextCompat.getColor(context, R.color.colorLevel))

            tv.isFocusable = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                tv.foreground = resources.getDrawable(R.drawable.clickable_item)

            tv.setOnClickListener {
                val l = pagerAdapter!!.getLevelOfIndex(mPager!!.currentItem)

                selectLeaderboard(category, if (l == null && mGame!!.levels != null && mGame!!.levels!!.isNotEmpty()) mGame!!.levels!![0] else l)
            }

            mLayoutCategory.addView(tv)
        }

        if (mGame!!.levels != null) {
            for (level in mGame!!.levels!!) {
                val tv = TextView(context)

                tv.text = level.name
                styleTab(tv)

                tv.isFocusable = true

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    tv.foreground = resources.getDrawable(R.drawable.clickable_item)

                tv.setOnClickListener { selectLeaderboard(pagerAdapter!!.getCategoryOfIndex(mPager!!.currentItem), level) }

                mLayoutLevel.addView(tv)
            }
        }
    }

    private fun getCenterScrollPosition(hsv: HorizontalScrollView, child: View): Int {
        return child.left + child.width / 2 - hsv.width / 2
    }

    private fun setScroll(categoryPos: Int, levelPos: Int, offset: Float) {
        // we want the tab to be as center aligned as possible.
        val categoryChild = mLayoutCategory.getChildAt(categoryPos)
        val categoryX1 = getCenterScrollPosition(mHsvCategory, categoryChild)

        var categoryX2 = categoryX1
        if (mLayoutCategory.childCount > categoryPos + 1 && (levelPos == -1 || levelPos + 1 >= mLayoutLevel.childCount))
            categoryX2 = getCenterScrollPosition(mHsvCategory, mLayoutCategory.getChildAt(categoryPos + 1))

        mHsvCategory.scrollTo(categoryX1 + floor(((categoryX2 - categoryX1).toFloat() * offset).toDouble()).toInt(), 0)

        if (levelPos != -1 && mLayoutLevel.getChildAt(levelPos) != null) {
            val levelChild = mLayoutLevel.getChildAt(levelPos)
            val levelX1 = getCenterScrollPosition(mHsvLevel, levelChild)

            val levelX2 = getCenterScrollPosition(mHsvLevel, mLayoutLevel.getChildAt((levelPos + 1) % mLayoutLevel.childCount))

            mHsvLevel.scrollTo(levelX1 + floor(((levelX2 - levelX1).toFloat() * offset).toDouble()).toInt(), 0)
        }

    }

    private fun hideLevelsStrip() {
        mHsvLevel.visibility = View.GONE
    }

    private fun showLevelsStrip() {
        mHsvLevel.visibility = View.VISIBLE
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        if (position < pagerAdapter!!.perGameCategorySize) {
            setScroll(position, -1, positionOffset)
        } else {
            setScroll(pagerAdapter!!.perGameCategorySize + (position - pagerAdapter!!.perGameCategorySize) / mGame!!.levels!!.size,
                    (position - pagerAdapter!!.perGameCategorySize) % mGame!!.levels!!.size, positionOffset)
        }
    }

    override fun onPageSelected(position: Int) {

        mLayoutCategory.getChildAt(mHighlightCategory).background = if(mHighlightCategory < pagerAdapter!!.perGameCategorySize) {
            ColorDrawable(ContextCompat.getColor(context, android.R.color.transparent))
        } else {
            ColorDrawable(ContextCompat.getColor(context, R.color.colorLevel))
        }

        if (position < pagerAdapter!!.perGameCategorySize) {
            mHighlightCategory = position
            mLayoutCategory.getChildAt(mHighlightCategory).background = ColorDrawable(ContextCompat.getColor(context, R.color.colorAccent))
            hideLevelsStrip()
        } else {
            mLayoutLevel.getChildAt(mHighlightLevel).background = ColorDrawable(ContextCompat.getColor(context, android.R.color.transparent))
            mHighlightCategory = pagerAdapter!!.perGameCategorySize + (position - pagerAdapter!!.perGameCategorySize) / mGame!!.levels!!.size
            mHighlightLevel = (position - pagerAdapter!!.perGameCategorySize) % mGame!!.levels!!.size
            mLayoutCategory.getChildAt(mHighlightCategory).background = ColorDrawable(ContextCompat.getColor(context, R.color.colorLevelSelected))
            mLayoutLevel.getChildAt(mHighlightLevel).background = ColorDrawable(ContextCompat.getColor(context, R.color.colorLevelSelected))
            showLevelsStrip()
        }
    }

    override fun onPageScrollStateChanged(state: Int) {}
}
