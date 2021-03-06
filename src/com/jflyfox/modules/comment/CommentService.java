package com.jflyfox.modules.comment;

import com.jflyfox.modules.article.ArticleService;
import com.jflyfox.modules.article.TbArticle;
import com.jflyfox.system.user.SysUser;
import com.jfinal.plugin.activerecord.Db;
import com.jflyfox.jfinal.base.BaseService;
import com.jflyfox.util.DateUtils;
import com.jflyfox.util.cache.Cache;
import com.jflyfox.util.cache.CacheManager;
import com.jflyfox.util.extend.HtmlUtils;

public class CommentService extends BaseService {

	private final static String cacheName = "CommentService";
	/**
	 * 未读评论缓存
	 */
	private static Cache cache = CacheManager.get(cacheName);

	/**
	 * 更新缓存,清空
	 * 
	 * 2015年4月29日 下午4:37:40 flyfox 330627517@qq.com
	 */
	public void updateCache() {
		cache.clear();
	}

	/**
	 * 保存评论
	 * 
	 * 2015年4月29日 下午3:11:37 flyfox 330627517@qq.com
	 * 
	 * @param user
	 * @param comment
	 */
	public void saveComment(SysUser user, TbComment comment) {
		// 评论
		// 删除标签
		String content = HtmlUtils.delHTMLTag(comment.getStr("content"));
		content = HtmlUtils.changeTag(content);

		comment.put("content", content);
		int status;
		if (comment.getInt("reply_userid") == 0) {
			// 评论自己文章 标记为已读
			status = (user.getUserID() == comment.getInt("create_id") ? CommentContants.STATUS_READ
					: CommentContants.STATUS_NO_READ);
			// 设置 回复人为文章创建者
			comment.put("reply_userid", comment.getInt("create_id"));
		} else { // 回复
			status = CommentContants.STATUS_REPLY_NO_READ;
		}
		comment.put("status", status);

		comment.put("fatherId", 0);
		comment.put("create_id", user.getUserID());
		comment.put("create_time", DateUtils.getNow(DateUtils.DEFAULT_REGEX_YYYY_MM_DD_HH_MIN_SS));
		comment.save();

		// 更新评论数
		updateArticleCommentCount(comment.getInt("article_id"));
		// 更新评论数缓存
		new ArticleService().addArticleCount(TbArticle.dao.findById(comment.getInt("article_id")));
		// 更新未读消息缓存
		getAndUpdateCommentUnreadCount(comment.getInt("reply_userid"), true);
	}

	/**
	 * 删除评论
	 * 
	 * 2015年4月29日 下午3:14:27 flyfox 330627517@qq.com
	 * 
	 * @param comment
	 */
	public void deleteComment(TbComment comment) {
		// 获取原始数据
		comment = TbComment.dao.findById(comment.getInt("id"));
		// 删除
		TbComment.dao.deleteById(comment.getInt("id"));
		// 更新评论数
		updateArticleCommentCount(comment.getInt("article_id"));
		// 缓存访问量和评论数
		new ArticleService().addArticleCount(TbArticle.dao.findById(comment.getInt("article_id")));
		// 更新未读消息缓存
		getAndUpdateCommentUnreadCount(comment.getInt("reply_userid"), true);
	}

	/**
	 * 更新文章评论数据
	 * 
	 * 2015年3月10日 下午3:25:46 flyfox 330627517@qq.com
	 * 
	 * @param article_id
	 */
	public void updateArticleCommentCount(int article_id) {
		String sql = "update tb_article set count_comment = " //
				+ "(select count(*) from tb_comment where article_id = ? ) where id = ? ";
		Db.update(sql, article_id, article_id);
	}

	/**
	 * 更新状态
	 * 
	 * 2015年3月10日 下午4:10:00 flyfox 330627517@qq.com
	 * 
	 * @param userid
	 * @param status
	 */
	public void updateCommentStatusRead(Integer userid) {
		String sql = "update tb_comment set status = status + 1 " //
				+ " where reply_userid = ? and " //
				+ "status in (" + CommentContants.STATUS_NO_READ //
				+ "," + CommentContants.STATUS_REPLY_NO_READ + ") ";
		Db.update(sql, userid);
		// 更新未读消息缓存，标记为已读
		getAndUpdateCommentUnreadCount(userid, true);
	}

	/**
	 * 获取未读数量
	 * 
	 * 由于评论数量会有大量请求，通过缓存实现
	 * 
	 * 2015年4月29日 下午2:53:23 flyfox 330627517@qq.com
	 * 
	 * @param userid
	 * @return
	 */
	public Object getCommentUnreadCount(int userid) {
		return getAndUpdateCommentUnreadCount(userid, false);
	}

	/**
	 * 获取未读数量
	 * 
	 * 由于评论数量会有大量请求，通过缓存实现
	 * 
	 * 2015年4月29日 下午2:53:23 flyfox 330627517@qq.com
	 * 
	 * @param userid
	 * @param userCache
	 *            是否强行更新缓存
	 * @return
	 */
	public Object getAndUpdateCommentUnreadCount(int userid, boolean updateCache) {
		Object cnt = cache.get(userid + "");
		if (cnt == null || updateCache) {
			String sql = "select count(*) AS cnt from tb_comment t " //
					+ " where t.reply_userid = ? " //
					+ " and status in (" + CommentContants.STATUS_NO_READ //
					+ "," + CommentContants.STATUS_REPLY_NO_READ + ") ";
			TbComment obj = TbComment.dao.findFirst(sql, userid);
			// 更新状态为已读
			cnt = obj.get("cnt");
			cache.add(userid + "", cnt);
		}

		return cnt;
	}
}
