"""국가자격 설명을 종목별 상세(개요·수행직무·진로·취득방법·변천과정·우대현황)로 보강.

이미 적재된 certificate 테이블의 국가(기술+전문) 행만 대상으로 description/search_text 를
재구성한다(민간 6만건 재적재 회피). load_cert_db.py 의 빌더를 그대로 재사용하므로
full reload(load_cert_db.py) 와 결과가 동일하다. 멱등(매번 원본에서 재생성).

사용: python enrich_national_cert_detail.py
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pymysql
from load_cert_db import DB, ncs_index, match_ncs, load_national_details, build_national_desc, national_search_text

GUBUN = {"NATIONAL_TECH": "국가기술자격", "NATIONAL_PROF": "국가전문자격"}


def main():
    conn = pymysql.connect(**DB)
    nidx = ncs_index(conn)
    ndet = load_national_details()
    with conn.cursor() as c:
        c.execute("SELECT id, cert_type, name, series FROM certificate "
                  "WHERE cert_type IN ('NATIONAL_TECH','NATIONAL_PROF')")
        rows = c.fetchall()
    updated = enriched = 0
    with conn.cursor() as c:
        for cid, ctype, name, series in rows:
            det = ndet.get(name, {})
            if det:
                enriched += 1
            ncs = match_ncs(name, nidx)
            gubun = GUBUN.get(ctype, "국가자격")
            desc = build_national_desc(gubun, series or "", ncs, det)
            stext = national_search_text(name, series or "", gubun, ncs, det)
            c.execute("UPDATE certificate SET description=%s, ncs_sub_name=%s, search_text=%s WHERE id=%s",
                      (desc, ncs, stext, cid))
            updated += c.rowcount
    conn.commit()
    print(f"국가자격 {len(rows)}건 중 상세보강 {enriched}건, description/search_text 갱신 {updated}건")
    conn.close()


if __name__ == "__main__":
    main()
