<template>
  <div class="policyPage">
    <button class="back" @click="$router.back()">← 返回</button>
    <h1>隐私政策</h1>
    <p class="updateDate">生效日期：2026年6月6日</p>

    <section>
      <p>Second hand（以下简称"我们"）深知个人信息对您的重要性。我们将按照法律法规要求，采取相应的安全保护措施，尽力保护您的个人信息安全可控。本隐私政策将帮助您了解我们如何收集、使用、存储和共享您的个人信息，以及您可以如何管理您的个人信息。</p>
    </section>

    <section>
      <h2>一、我们收集的信息</h2>

      <h3>1.1 您主动提供的信息</h3>
      <ul>
        <li><strong>账户信息：</strong>手机号、昵称、头像、登录密码（加密存储）。</li>
        <li><strong>交易信息：</strong>发布的商品信息（标题、描述、图片、价格）、收货地址、收货人姓名及电话。</li>
        <li><strong>聊天信息：</strong>通过平台站内聊天功能发送的消息内容。</li>
        <li><strong>反馈信息：</strong>举报内容、售后申请理由、协商记录、平台仲裁提交的举证材料。</li>
      </ul>

      <h3>1.2 我们自动收集的信息</h3>
      <ul>
        <li><strong>设备信息：</strong>设备型号、操作系统版本、浏览器类型、IP地址。</li>
        <li><strong>日志信息：</strong>访问时间、浏览记录、搜索关键词、点击行为。</li>
        <li><strong>Cookie及类似技术：</strong>用于维持登录状态、记住偏好设置。</li>
      </ul>
    </section>

    <section>
      <h2>二、信息的使用</h2>
      <p>我们使用收集的信息用于以下目的：</p>
      <ul>
        <li>创建和管理您的账户；</li>
        <li>展示商品信息并撮合交易；</li>
        <li>提供站内即时通讯服务；</li>
        <li>处理举报、售后及纠纷仲裁；</li>
        <li>基于您的浏览和收藏偏好提供个性化商品推荐；</li>
        <li>保障平台安全，检测和防范欺诈、滥用行为；</li>
        <li>遵守法律法规要求，配合监管机构的合法请求。</li>
      </ul>
    </section>

    <section>
      <h2>三、信息的共享与披露</h2>
      <p>我们不会将您的个人信息出售给第三方。在以下情况下我们可能会共享您的信息：</p>
      <ul>
        <li><strong>交易对方：</strong>为完成交易，我们会向卖家展示买家的收货信息（姓名、电话、地址）；买家可以在卖家信息页查看卖家的公开信息（昵称、历史评价）。</li>
        <li><strong>法律要求：</strong>根据法律法规、法院命令或政府机关的要求进行披露。</li>
        <li><strong>平台安全保障：</strong>为检测和防范欺诈，可能会与反欺诈服务商共享必要的交易数据。</li>
        <li><strong>经您同意：</strong>在获得您明确同意的情况下，与其他方共享您的信息。</li>
      </ul>
    </section>

    <section>
      <h2>四、信息的存储与安全</h2>
      <ul>
        <li><strong>存储地点：</strong>您的个人信息存储于中华人民共和国境内的服务器。</li>
        <li><strong>存储期限：</strong>我们仅在实现本政策所述目的所需的期限内保留您的个人信息，除非法律有强制性的留存要求。账号注销后，您的个人信息将在合理期限内进行匿名化处理或删除。</li>
        <li><strong>安全措施：</strong>我们采用密码加密存储（bcrypt）、HTTPS传输加密、访问权限控制等技术手段保护您的信息安全。但请注意，没有任何互联网传输或存储方式是100%安全的。</li>
      </ul>
    </section>

    <section>
      <h2>五、您的权利</h2>
      <ul>
        <li><strong>访问与更正：</strong>您可以在"个人中心"随时查看和修改您的昵称、头像、收货地址等个人信息。</li>
        <li><strong>删除：</strong>您可以删除您的商品、收货地址。已完成的交易记录根据法律要求保留，无法删除。</li>
        <li><strong>注销账户：</strong>您可以联系平台客服申请注销账户。注销后，您的个人信息将被删除或匿名化处理，但依法留存的信息除外。</li>
        <li><strong>撤回同意：</strong>您可以通过浏览器设置拒绝Cookie。部分功能可能因此受限。</li>
      </ul>
    </section>

    <section>
      <h2>六、未成年人保护</h2>
      <p>本平台不对未成年人开放注册和使用。如果您是未满18周岁的未成年人，请在监护人指导下使用本平台，并由监护人阅读并同意本隐私政策。</p>
    </section>

    <section>
      <h2>七、第三方服务</h2>
      <p>本平台可能包含指向第三方网站或服务的链接。我们对第三方的隐私实践不承担责任，建议您在使用第三方服务前阅读其隐私政策。</p>
    </section>

    <section>
      <h2>八、政策更新</h2>
      <p>我们可能会不时更新本隐私政策。更新后的政策将在平台公布，重大变更我们会通过站内消息或弹窗方式通知您。继续使用平台即表示您同意更新后的政策。</p>
    </section>

    <section>
      <h2>九、联系我们</h2>
      <p>如果您对本隐私政策有任何疑问或建议，请通过以下方式联系我们：</p>
      <ul>
        <li>站内消息：联系平台管理员</li>
        <li>电子邮件：privacy@secondhand.example.com</li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.policyPage {
  max-width: 760px; margin: 0 auto; padding: 16px 0 60px;
  font-size: 14px; line-height: 1.75; color: rgba(0,0,0,0.8);
}
h1 { font-size: 22px; margin: 12px 0 4px; }
.updateDate { color: rgba(0,0,0,0.35); font-size: 12px; margin: 0 0 24px; }
h2 { font-size: 17px; margin: 28px 0 10px; padding-bottom: 6px; border-bottom: 1px solid rgba(0,0,0,0.08); }
h3 { font-size: 15px; margin: 16px 0 8px; }
ul, ol { padding-left: 22px; }
li { margin-bottom: 4px; }
section { margin-bottom: 16px; }
.back { border: 1px solid rgba(0,0,0,0.12); background: white; padding: 6px 10px; border-radius: 10px; cursor: pointer; margin-bottom: 10px; }
</style>
