//package io.oz.srv;
//
//import java.io.IOException;
//import java.net.InetAddress;
//
//import io.odysz.semantic.jprotocol.AnsonMsg;
//import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
//import io.odysz.semantic.jserv.ServPort6;
//import io.odysz.semantics.x.SemanticException;
//import io.oz.srv.respaths.HtmlPort;
//import io.oz.srv.respaths.ResPathsReq;
//import io.oz.srv.respaths.ResPathsReq.A;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.HttpServletResponse;
//
///**
// * Port {@link HtmlPort#respaths}, for the local synode to trigger a refresh of published paths.
// * No session, requests from localhost only.
// * 
// * @since 0.2.1
// */
//public class ResPaths extends ServPort6<ResPathsReq> {
//	private static final long serialVersionUID = 1L;
//
//	final ResPathsTier tier;
//
//	public ResPaths(ResPathsTier tier) {
//		super(HtmlPort.respaths);
//		this.tier = tier;
//	}
//
//	@Override
//	protected void onGet(AnsonMsg<ResPathsReq> msg, HttpServletResponse resp)
//			throws ServletException, IOException, SemanticException {
//		onPost(msg, resp);
//	}
//
//	@Override
//	protected void onPost(AnsonMsg<ResPathsReq> msg, HttpServletResponse resp)
//			throws ServletException, IOException, SemanticException {
//
//		if (!InetAddress.getByName(msg.addr()).isLoopbackAddress())
//			throw new SemanticException("Remote requests, from %s, are not allowed.", msg.addr());
//
//		ResPathsReq req = msg.body(0);
//		if (A.refresh.equals(req.a()))
//			try {
//				write(resp, ok(String.join(",", tier.reconcile())));
//			} catch (Exception e) {
//				write(resp, err(MsgCode.exGeneral, "%s: %s", e.getClass().getName(), e.getMessage()));
//			}
//		else
//			throw new SemanticException("Unknown request: a = %s", req.a());
//	}
//}
